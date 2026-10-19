package app.fuelwise.sync.drive

import android.content.Context
import app.fuelwise.core.data.DeviceIdProvider
import app.fuelwise.core.data.repository.BackupSettingsRepository
import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.database.DatabaseSnapshotter
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.BackupError
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface BackupOutcome {
    data class Success(val sizeBytes: Long) : BackupOutcome

    data class Failed(val error: BackupError) : BackupOutcome
}

sealed interface RestoreOutcome {
    /** [rows] restored per table, for the "what came back" summary. */
    data class Success(val rows: Map<String, Int>) : RestoreOutcome

    data object NeedsPassphrase : RestoreOutcome

    data object WrongPassphrase : RestoreOutcome

    data class Failed(val error: BackupError) : RestoreOutcome
}

/**
 * Backup and restore (PRD F-14, R1 scope): snapshots to the user's Drive app folder or to a file they pick, encrypted
 * when they chose a passphrase. One operation at a time; every step is safe to rerun after a crash or a kill.
 */
@Singleton
class BackupManager @Inject constructor(
    private val snapshotter: DatabaseSnapshotter,
    private val settings: BackupSettingsRepository,
    private val deviceIds: DeviceIdProvider,
    private val documents: DocumentRepository,
    private val drive: DriveClient,
    private val auth: DriveAuth,
    private val keys: BackupKeyStore,
    private val clock: Clock,
    private val zoneId: ZoneId,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    val isConfigured: Boolean get() = auth.isConfigured

    /** Sign in with Google and ask for the Drive app folder (PRD F-14.2). */
    suspend fun connect(activityContext: Context): DriveAccess {
        if (!auth.isConfigured) return DriveAccess.NotConfigured
        val email = auth.signIn(activityContext)
        settings.update { it.copy(accountEmail = email, lastError = null) }
        return auth.authorize(email)
    }

    suspend fun disconnect() = settings.update { it.copy(accountEmail = null, lastError = null) }

    suspend fun backupNow(): BackupOutcome = mutex.withLock {
        val now = clock.millis()
        settings.update { it.copy(lastAttemptUtcMs = now) }
        val outcome = runCatchingDrive { token ->
            val key = if (settings.settings.first().encrypted) {
                keys.load()
                    ?: return@runCatchingDrive BackupOutcome.Failed(BackupError.KeyMissing)
            } else {
                null
            }
            val bytes = snapshotBytes(key, now)
            val existing = drive.list(token)
            if (key != null) uploadAttachments(token, key, existing)
            val name = RemoteBackup.snapshotName(now, deviceIds.deviceId(), encrypted = key != null)
            val id = drive.upload(token, name, bytes)
            val all = existing + RemoteBackup(id, name, now, bytes.size.toLong())
            snapshotsToDelete(all, now, zoneId).filter { it.id != id }.forEach { drive.delete(token, it.id) }
            BackupOutcome.Success(bytes.size.toLong())
        }.let { result ->
            when (result) {
                is DriveResult.Ok -> result.value
                is DriveResult.Error -> BackupOutcome.Failed(result.error)
            }
        }
        settings.update { s ->
            when (outcome) {
                is BackupOutcome.Success -> s.copy(
                    lastSuccessUtcMs = now,
                    lastSizeBytes = outcome.sizeBytes,
                    lastError = null,
                )

                is BackupOutcome.Failed -> s.copy(lastError = outcome.error)
            }
        }
        outcome
    }

    suspend fun listBackups(): Result<List<RemoteBackup>> = when (val r = runCatchingDrive { drive.list(it) }) {
        is DriveResult.Ok -> Result.success(r.value.filter { it.isSnapshot }.sortedByDescending { it.createdAtUtcMs })
        is DriveResult.Error -> Result.failure(IOException(r.error.name))
    }

    /**
     * Restores a Drive snapshot atomically (PRD F-14.7). Pass [passphrase] for encrypted backups made on another
     * device.
     */
    suspend fun restore(backup: RemoteBackup, passphrase: CharArray?): RestoreOutcome = mutex.withLock {
        val result = runCatchingDrive { token ->
            val bytes = drive.download(token, backup.id)
            restoreBytes(bytes, passphrase) { key, attachments -> restoreAttachments(token, key, attachments) }
        }
        when (result) {
            is DriveResult.Ok -> result.value
            is DriveResult.Error -> RestoreOutcome.Failed(result.error)
        }
    }

    /** "Save backup file" (PRD F-14.9): the same snapshot, for users who don't sign in. Photos stay on the device. */
    suspend fun exportFile(): ByteArray = mutex.withLock {
        val key = if (settings.settings.first().encrypted) keys.load() else null
        snapshotBytes(key, clock.millis())
    }

    suspend fun importFile(bytes: ByteArray, passphrase: CharArray?): RestoreOutcome = mutex.withLock {
        try {
            restoreBytes(bytes, passphrase) { _, _ -> }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RestoreOutcome.Failed(BackupError.Unknown)
        }
    }

    fun needsPassphrase(bytes: ByteArray): Boolean = BackupCrypto.isEncrypted(bytes)

    /** Turns on encrypted backups; [passphrase] was typed twice. Derivation takes a moment, off the main thread. */
    suspend fun enableEncryption(passphrase: CharArray) {
        val key = withContext(io) { BackupCrypto.deriveKey(passphrase, BackupCrypto.newParams()) }
        keys.save(key)
        settings.update { it.copy(encrypted = true, lastError = null) }
    }

    /**
     * "Delete all data" with "also delete Drive backups" (PRD F-18): removes every file in the app folder, snapshots
     * and encrypted photos alike. Returns null on success, or what stopped it.
     */
    suspend fun deleteDriveBackups(): BackupError? = mutex.withLock {
        when (val r = runCatchingDrive { token -> drive.list(token).forEach { drive.delete(token, it.id) } }) {
            is DriveResult.Ok -> null
            is DriveResult.Error -> r.error
        }
    }

    /** Full reset: forgets the backup key on this device. The settings themselves reset with the preferences. */
    suspend fun forgetKey() = mutex.withLock { keys.clear() }

    suspend fun disableEncryption() {
        keys.clear()
        settings.update { it.copy(encrypted = false) }
    }

    private suspend fun snapshotBytes(key: BackupKey?, now: Long): ByteArray = withContext(io) {
        val dump = snapshotter.dump().let { d ->
            // Without encryption the documents vault and trip routes (attachments) stay on the device (PRD F-12,
            // F-14.5, F-21.6). Raw trip points never leave: they are sealed with this device's Keystore key and only
            // exist while a trip records.
            val local = d.tables - DEVICE_ONLY_TABLES
            d.copy(tables = if (key == null) local - VAULT_TABLES else local)
        }
        val gz = SnapshotCodec.encode(
            Snapshot(SnapshotCodec.FORMAT, now, deviceIds.deviceId(), dump, settings.exportPreferences()),
        )
        if (key == null) gz else BackupCrypto.encrypt(key, gz)
    }

    private suspend fun restoreBytes(
        bytes: ByteArray,
        passphrase: CharArray?,
        attachments: suspend (BackupKey, List<Attachment>) -> Unit,
    ): RestoreOutcome {
        var key: BackupKey? = null
        val gz = if (BackupCrypto.isEncrypted(bytes)) {
            key = passphrase?.let { withContext(io) { BackupCrypto.deriveKey(it, BackupCrypto.paramsOf(bytes)) } }
                ?: keys.load()?.takeIf { it.params == BackupCrypto.paramsOf(bytes) }
                ?: return RestoreOutcome.NeedsPassphrase
            try {
                BackupCrypto.decrypt(key, bytes)
            } catch (_: WrongPassphraseException) {
                return RestoreOutcome.WrongPassphrase
            }
        } else {
            bytes
        }
        val snapshot = withContext(io) { SnapshotCodec.decode(gz) }
        withContext(io) { snapshotter.replaceAll(snapshot.dump) }
        settings.importPreferences(snapshot.preferences)
        if (key != null) {
            // Backups after this one stay encrypted with the same passphrase.
            keys.save(key)
            settings.update { it.copy(encrypted = true) }
            attachments(key, documents.allAttachments())
        }
        return RestoreOutcome.Success(snapshot.dump.tables.mapValues { it.value.size })
    }

    /** Content-addressed: each photo uploads once, under a name that reveals nothing about it. */
    private suspend fun uploadAttachments(token: String, key: BackupKey, existing: List<RemoteBackup>) {
        val present = existing.map { it.name }.toSet()
        documents.allAttachments().forEach { a ->
            val name = key.attachmentName(a.sha256)
            if (name !in present) drive.upload(token, name, BackupCrypto.encrypt(key, documents.readAttachment(a)))
        }
    }

    private suspend fun restoreAttachments(token: String, key: BackupKey, attachments: List<Attachment>) {
        val remote = drive.list(token).associateBy { it.name }
        attachments.forEach { a ->
            remote[key.attachmentName(a.sha256)]?.let { file ->
                documents.restoreAttachmentFile(a, BackupCrypto.decrypt(key, drive.download(token, file.id)))
            }
        }
    }

    private sealed interface DriveResult<out T> {
        data class Ok<T>(val value: T) : DriveResult<T>

        data class Error(val error: BackupError) : DriveResult<Nothing>
    }

    /** Signs in silently, runs [block] with a token and maps failures to what the status screen can fix. */
    private suspend fun <T> runCatchingDrive(block: suspend (String) -> T): DriveResult<T> {
        val email = settings.settings.first().accountEmail ?: return DriveResult.Error(BackupError.SignIn)
        val token = when (val access = runCatching { auth.authorize(email) }.getOrNull()) {
            is DriveAccess.Granted -> access.accessToken
            else -> return DriveResult.Error(BackupError.SignIn)
        }
        return try {
            DriveResult.Ok(block(token))
        } catch (_: DriveException.Unauthorized) {
            DriveResult.Error(BackupError.SignIn)
        } catch (_: DriveException.StorageFull) {
            DriveResult.Error(BackupError.StorageFull)
        } catch (_: IOException) {
            DriveResult.Error(BackupError.Offline)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A corrupt file, a newer backup format or a failed restore transaction: nothing was changed.
            DriveResult.Error(BackupError.Unknown)
        }
    }

    private companion object {
        val VAULT_TABLES = setOf("document", "attachment")
        val DEVICE_ONLY_TABLES = setOf("trip_point_batch")
    }
}

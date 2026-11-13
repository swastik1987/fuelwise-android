package app.fuelwise.feature.backup

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.BackupSettingsRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.model.BackupError
import app.fuelwise.core.model.BackupFrequency
import app.fuelwise.core.model.BackupSettings
import app.fuelwise.sync.drive.BackupManager
import app.fuelwise.sync.drive.BackupOutcome
import app.fuelwise.sync.drive.BackupScheduler
import app.fuelwise.sync.drive.DriveAccess
import app.fuelwise.sync.drive.DriveAuth
import app.fuelwise.sync.drive.RemoteBackup
import app.fuelwise.sync.drive.RestoreOutcome
import app.fuelwise.sync.drive.VisibleCopyOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What's in progress, so buttons disable and the right spinner shows. */
enum class BackupBusy { Connecting, BackingUp, Listing, Restoring, Encrypting, Exporting, SavingCopy }

/** A restore waiting for something: the user's confirmation, or the passphrase of an encrypted backup. */
sealed interface PendingRestore {
    data class Drive(val backup: RemoteBackup) : PendingRestore

    class File(val bytes: ByteArray) : PendingRestore
}

data class BackupUiState(
    val settings: BackupSettings = BackupSettings(),
    val configured: Boolean = true,
    val busy: BackupBusy? = null,
    val backups: List<RemoteBackup>? = null,
    val confirmRestore: PendingRestore? = null,
    val askPassphrase: PendingRestore? = null,
    val wrongPassphrase: Boolean = false,
    val nowUtcMs: Long = 0,
)

sealed interface BackupEvent {
    /** Google wants the user to confirm Drive access. */
    data class Consent(val sender: IntentSender) : BackupEvent

    data class BackedUp(val sizeBytes: Long) : BackupEvent

    data class Failed(val error: BackupError) : BackupEvent

    data class Restored(val entries: Int) : BackupEvent

    data object Exported : BackupEvent

    /** The visible copy is in My Drive, in the folder [folder]. */
    data class CopySaved(val folder: String) : BackupEvent
}

/** Backup & sync (design.md screen 27; PRD F-14). */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val settings: BackupSettingsRepository,
    private val backups: BackupManager,
    private val auth: DriveAuth,
    private val scheduler: BackupScheduler,
    private val clock: Clock,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : ViewModel() {
    private val local = MutableStateFlow(BackupUiState(configured = backups.isConfigured))

    val state: StateFlow<BackupUiState> = combine(settings.settings, local) { s, l ->
        l.copy(settings = s, nowUtcMs = clock.millis())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    private val eventChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /** The consent screen on screen is for the visible copy's scope, not the backup one. */
    private var copyConsentPending = false

    /** Sign in and grant Drive access; needs the Activity for Credential Manager's account picker. */
    fun connect(activityContext: Context) = op(BackupBusy.Connecting) {
        val access = runCatching { backups.connect(activityContext) }.getOrElse {
            eventChannel.send(BackupEvent.Failed(BackupError.SignIn))
            return@op
        }
        handleAccess(access)
    }

    fun onConsentResult(data: Intent?) {
        val forCopy = copyConsentPending
        copyConsentPending = false
        op(if (forCopy) BackupBusy.SavingCopy else BackupBusy.Connecting) {
            val access = runCatching { auth.complete(data) }.getOrElse { DriveAccess.NotConfigured }
            when {
                !forCopy -> handleAccess(access)
                access is DriveAccess.Granted -> saveCopy()
                else -> Unit // Declined: nothing to say, the button is still there.
            }
        }
    }

    /** "Save a copy to My Drive" (PRD F-14.1): one visible file, replaced each time. */
    fun saveVisibleCopy() = op(BackupBusy.SavingCopy) { saveCopy() }

    private suspend fun saveCopy() {
        when (val outcome = backups.saveVisibleCopy()) {
            is VisibleCopyOutcome.Saved -> eventChannel.send(BackupEvent.CopySaved(BackupManager.VISIBLE_FOLDER))

            is VisibleCopyOutcome.NeedsConsent -> {
                copyConsentPending = true
                eventChannel.send(BackupEvent.Consent(outcome.intent.intentSender))
            }

            is VisibleCopyOutcome.Failed -> eventChannel.send(BackupEvent.Failed(outcome.error))
        }
    }

    private suspend fun handleAccess(access: DriveAccess) {
        when (access) {
            is DriveAccess.Granted -> {
                settings.update { it.copy(lastError = null) }
                scheduler.runNow()
            }

            is DriveAccess.NeedsConsent -> eventChannel.send(BackupEvent.Consent(access.intent.intentSender))

            DriveAccess.NotConfigured -> eventChannel.send(BackupEvent.Failed(BackupError.SignIn))
        }
    }

    fun disconnect() = viewModelScope.launch { backups.disconnect() }

    fun setFrequency(frequency: BackupFrequency) = viewModelScope.launch {
        settings.update { it.copy(frequency = frequency) }
    }

    fun setWifiOnly(on: Boolean) = viewModelScope.launch { settings.update { it.copy(wifiOnly = on) } }

    fun setChargingOnly(on: Boolean) = viewModelScope.launch { settings.update { it.copy(chargingOnly = on) } }

    fun backUpNow() = op(BackupBusy.BackingUp) {
        when (val outcome = backups.backupNow()) {
            is BackupOutcome.Success -> eventChannel.send(BackupEvent.BackedUp(outcome.sizeBytes))
            is BackupOutcome.Failed -> eventChannel.send(BackupEvent.Failed(outcome.error))
        }
    }

    fun enableEncryption(passphrase: CharArray) = op(BackupBusy.Encrypting) {
        backups.enableEncryption(passphrase)
        passphrase.fill(' ')
    }

    fun disableEncryption() = viewModelScope.launch { backups.disableEncryption() }

    fun loadBackups() = op(BackupBusy.Listing) {
        backups.listBackups()
            .onSuccess { list -> local.update { it.copy(backups = list) } }
            .onFailure { eventChannel.send(BackupEvent.Failed(BackupError.Offline)) }
    }

    fun closeBackups() = local.update { it.copy(backups = null) }

    fun askRestore(backup: RemoteBackup) = local.update {
        it.copy(backups = null, confirmRestore = PendingRestore.Drive(backup))
    }

    fun pickFile(uri: Uri) = viewModelScope.launch {
        val bytes =
            withContext(io) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: return@launch
        local.update { it.copy(confirmRestore = PendingRestore.File(bytes)) }
    }

    fun cancelRestore() = local.update { it.copy(confirmRestore = null, askPassphrase = null, wrongPassphrase = false) }

    /** Confirmed "replace what's on this phone"; [passphrase] when the backup is encrypted. */
    fun restore(passphrase: CharArray? = null) {
        val pending = local.value.confirmRestore ?: local.value.askPassphrase ?: return
        op(BackupBusy.Restoring) {
            val outcome = when (pending) {
                is PendingRestore.Drive -> backups.restore(pending.backup, passphrase)
                is PendingRestore.File -> backups.importFile(pending.bytes, passphrase)
            }
            passphrase?.fill(' ')
            when (outcome) {
                is RestoreOutcome.Success -> {
                    local.update { it.copy(confirmRestore = null, askPassphrase = null, wrongPassphrase = false) }
                    eventChannel.send(BackupEvent.Restored(outcome.rows.filterKeys { it in ENTRY_TABLES }.values.sum()))
                }

                RestoreOutcome.NeedsPassphrase -> local.update {
                    it.copy(confirmRestore = null, askPassphrase = pending)
                }

                RestoreOutcome.WrongPassphrase -> local.update {
                    it.copy(confirmRestore = null, askPassphrase = pending, wrongPassphrase = true)
                }

                is RestoreOutcome.Failed -> {
                    local.update { it.copy(confirmRestore = null, askPassphrase = null) }
                    eventChannel.send(BackupEvent.Failed(outcome.error))
                }
            }
        }
    }

    /** "Save backup file to device" (PRD F-14.9) to a place the user picked. */
    fun exportTo(uri: Uri) = op(BackupBusy.Exporting) {
        val ok = runCatching {
            val bytes = backups.exportFile()
            withContext(io) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("No output")
            }
        }.isSuccess
        eventChannel.send(if (ok) BackupEvent.Exported else BackupEvent.Failed(BackupError.Unknown))
    }

    val exportFileName: String get() = "fuelwise-backup-${java.time.LocalDate.now(clock)}.json.gz"

    private fun op(busy: BackupBusy, block: suspend () -> Unit) {
        if (local.value.busy != null) return
        viewModelScope.launch {
            local.update { it.copy(busy = busy) }
            try {
                block()
            } finally {
                local.update { it.copy(busy = null) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val ENTRY_TABLES = setOf("refuel", "expense")
    }
}

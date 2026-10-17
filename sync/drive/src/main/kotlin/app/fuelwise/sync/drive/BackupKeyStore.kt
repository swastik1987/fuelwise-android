package app.fuelwise.sync.drive

import android.content.Context
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Keeps the passphrase-derived backup key on this device so scheduled backups run without asking. The passphrase is
 * never stored; the key is wrapped by a Tink keyset whose master key lives in the Android Keystore.
 */
interface BackupKeyStore {
    suspend fun load(): BackupKey?

    suspend fun save(key: BackupKey)

    suspend fun clear()
}

@Singleton
internal class TinkBackupKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : BackupKeyStore {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val file: File get() = File(context.noBackupFilesDir, FILE_NAME)

    override suspend fun load(): BackupKey? = withContext(io) {
        if (!file.exists()) return@withContext null
        runCatching {
            val plain = ByteBuffer.wrap(aead.decrypt(file.readBytes(), AAD))
            val key = ByteArray(plain.int).also { plain.get(it) }
            val salt = ByteArray(plain.int).also { plain.get(it) }
            BackupKey(key, KdfParams(salt, plain.int, plain.int, plain.int))
        }.getOrNull()
    }

    override suspend fun save(key: BackupKey) {
        withContext(io) {
            val p = key.params
            val plain = ByteBuffer.allocate(Int.SIZE_BYTES * INT_FIELDS + key.bytes.size + p.salt.size)
                .putInt(key.bytes.size).put(key.bytes)
                .putInt(p.salt.size).put(p.salt)
                .putInt(p.memoryKiB).putInt(p.iterations).putInt(p.parallelism)
                .array()
            file.writeBytes(aead.encrypt(plain, AAD))
        }
    }

    override suspend fun clear() {
        withContext(io) { file.delete() }
    }

    private companion object {
        const val FILE_NAME = "backup.key"

        // Key length, salt length, memory, iterations, parallelism.
        const val INT_FIELDS = 5
        const val KEYSET_NAME = "fw_backup_keyset"
        const val KEYSET_PREFS = "fw_backup_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://fw_backup_master_key"
        val AAD = "fuelwise-backup-key".toByteArray()
    }
}

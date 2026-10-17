package app.fuelwise.core.data.vault

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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Encrypted files for documents and receipts, in app-private storage (PRD F-12). */
interface AttachmentStore {
    /** Encrypts [plaintext] bound to [attachmentId] and returns the stored file name. */
    suspend fun write(attachmentId: String, plaintext: ByteArray): String

    suspend fun read(attachmentId: String, storedName: String): ByteArray

    suspend fun delete(storedName: String)
}

/**
 * Tink AES-256-GCM with the keyset wrapped by an Android Keystore key (CLAUDE.md stack). The attachment id is the
 * associated data, so a file can't be swapped under another row.
 */
@Singleton
internal class TinkAttachmentStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : AttachmentStore {
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

    private val dir: File get() = File(context.filesDir, VAULT_DIR).apply { mkdirs() }

    override suspend fun write(attachmentId: String, plaintext: ByteArray): String = withContext(io) {
        val name = "$attachmentId.bin"
        File(dir, name).writeBytes(aead.encrypt(plaintext, attachmentId.toByteArray()))
        name
    }

    override suspend fun read(attachmentId: String, storedName: String): ByteArray = withContext(io) {
        aead.decrypt(File(dir, storedName).readBytes(), attachmentId.toByteArray())
    }

    override suspend fun delete(storedName: String) {
        withContext(io) { File(dir, storedName).delete() }
    }

    private companion object {
        const val VAULT_DIR = "vault"
        const val KEYSET_NAME = "fw_vault_keyset"
        const val KEYSET_PREFS = "fw_vault_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://fw_vault_master_key"
    }
}

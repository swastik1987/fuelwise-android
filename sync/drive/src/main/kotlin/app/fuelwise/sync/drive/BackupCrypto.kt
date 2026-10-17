package app.fuelwise.sync.drive

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** A key derived from the backup passphrase, with what's needed to derive it again on another device. */
class BackupKey(val bytes: ByteArray, val params: KdfParams) {
    /** Content-free name for an attachment: knowing a file's hash from elsewhere doesn't reveal it's backed up. */
    fun attachmentName(sha256: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(bytes, "HmacSHA256")) }
        return "att-" + mac.doFinal(sha256.toByteArray()).joinToString("") { "%02x".format(it) }.take(NAME_HEX) + ".bin"
    }

    private companion object {
        const val NAME_HEX = 40
    }
}

/** Argon2id parameters, stored in every encrypted file's header so they can change later without breaking old files. */
data class KdfParams(
    val salt: ByteArray,
    val memoryKiB: Int = MEMORY_KIB,
    val iterations: Int = ITERATIONS,
    val parallelism: Int = 1,
) {
    override fun equals(other: Any?) = other is KdfParams && salt.contentEquals(other.salt) &&
        memoryKiB == other.memoryKiB && iterations == other.iterations && parallelism == other.parallelism

    override fun hashCode() = salt.contentHashCode() * 31 + memoryKiB

    companion object {
        // OWASP's minimum for Argon2id (19 MiB, t=2, p=1): a few hundred ms on a mid-range phone.
        const val MEMORY_KIB = 19 * 1024
        const val ITERATIONS = 2
    }
}

class WrongPassphraseException : Exception("The passphrase doesn't match this backup")

/**
 * Encrypted backups (PRD F-14.5): Argon2id turns the passphrase into a 256-bit key; AES-256-GCM seals each file.
 * File layout: "FWE1" · memory KiB (4) · iterations (1) · parallelism (1) · salt (16) · nonce (12) · sealed data.
 * Everything before the ciphertext is authenticated as associated data.
 */
object BackupCrypto {
    private val MAGIC = "FWE1".toByteArray()
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 4 + 1 + 1 + SALT_BYTES
    private val random = SecureRandom()

    fun newParams(): KdfParams = KdfParams(ByteArray(SALT_BYTES).also(random::nextBytes))

    fun deriveKey(passphrase: CharArray, params: KdfParams): BackupKey {
        val generator = Argon2BytesGenerator().apply {
            init(
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(params.salt)
                    .withMemoryAsKB(params.memoryKiB)
                    .withIterations(params.iterations)
                    .withParallelism(params.parallelism)
                    .build(),
            )
        }
        val out = ByteArray(KEY_BYTES)
        generator.generateBytes(passphrase, out)
        return BackupKey(out, params)
    }

    fun isEncrypted(data: ByteArray): Boolean =
        data.size > HEADER_BYTES && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** The derivation parameters an encrypted file was made with, to derive the key from a typed passphrase. */
    fun paramsOf(data: ByteArray): KdfParams {
        require(isEncrypted(data)) { "Not an encrypted backup" }
        val buf = ByteBuffer.wrap(data, MAGIC.size, HEADER_BYTES - MAGIC.size)
        val memory = buf.int
        val iterations = buf.get().toInt()
        val parallelism = buf.get().toInt()
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        return KdfParams(salt, memory, iterations, parallelism)
    }

    fun encrypt(key: BackupKey, plaintext: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .putInt(key.params.memoryKiB)
            .put(key.params.iterations.toByte())
            .put(key.params.parallelism.toByte())
            .put(key.params.salt)
            .array()
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
        return header + nonce + cipher.doFinal(plaintext)
    }

    fun decrypt(key: BackupKey, data: ByteArray): ByteArray {
        require(isEncrypted(data)) { "Not an encrypted backup" }
        val header = data.copyOfRange(0, HEADER_BYTES)
        val nonce = data.copyOfRange(HEADER_BYTES, HEADER_BYTES + NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
        return try {
            cipher.doFinal(data, HEADER_BYTES + NONCE_BYTES, data.size - HEADER_BYTES - NONCE_BYTES)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassphraseException().apply { initCause(e) }
        }
    }
}

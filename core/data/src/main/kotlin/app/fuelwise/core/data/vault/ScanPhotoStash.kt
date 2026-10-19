package app.fuelwise.core.data.vault

import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.model.Attachment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a scan's photo in memory between the camera and the entry it belongs to (PRD F-15). The entry doesn't exist
 * until the user saves it, and an unencrypted photo must not sit on disk meanwhile (CLAUDE.md rule 5), so only the
 * saved entry writes it to the encrypted vault. Lost on process death, which only drops an optional photo.
 */
@Singleton
class ScanPhotoStash @Inject constructor(private val ids: IdGenerator, private val documents: DocumentRepository) {
    private val photos = LinkedHashMap<String, ByteArray>()

    /** Keeps [jpeg] and returns the token to [take] it with. Only the latest few photos are kept. */
    @Synchronized
    fun put(jpeg: ByteArray): String {
        val token = ids.newId()
        photos[token] = jpeg
        while (photos.size > MAX_PHOTOS) photos.remove(photos.keys.first())
        return token
    }

    @Synchronized
    fun peek(token: String): ByteArray? = photos[token]

    /** Removes and returns the photo; null when it was dropped or already taken. */
    @Synchronized
    fun take(token: String): ByteArray? = photos.remove(token)

    /** Moves the photo into the encrypted vault, attached to the saved fill-up [refuelId]. False when it was gone. */
    suspend fun attachToRefuel(token: String, refuelId: String): Boolean {
        val jpeg = take(token) ?: return false
        documents.addAttachment(refuelId, Attachment.OwnerType.Refuel, jpeg, JPEG)
        return true
    }

    private companion object {
        const val JPEG = "image/jpeg"
        const val MAX_PHOTOS = 3
    }
}

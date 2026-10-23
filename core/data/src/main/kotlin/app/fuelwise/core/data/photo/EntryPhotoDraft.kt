package app.fuelwise.core.data.photo

import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.model.Attachment
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * The photos of a fill-up or an expense while its sheet is open (PRD F-02.1, F-04.1). New photos stay in memory and
 * removals are only noted, so closing without saving changes nothing; [commit] writes both once the entry is saved.
 * One per sheet: not a singleton.
 */
class EntryPhotoDraft @Inject constructor(private val documents: DocumentRepository) {
    /** A saved photo ([attachment]) or one picked in this sheet ([jpeg]). */
    class Item(val key: String, val jpeg: ByteArray?, val attachment: Attachment?)

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()
    private val removed = mutableListOf<Attachment>()
    private var nextKey = 0

    val canAdd: Boolean get() = _items.value.size < MAX_PHOTOS

    /** The entry's saved photos, when editing. */
    suspend fun load(ownerId: String) {
        _items.value = documents.observeAttachments(ownerId).first()
            .filter { it.mimeType.startsWith(IMAGE) }
            .map { Item(it.id, null, it) }
    }

    fun add(jpeg: ByteArray) {
        if (canAdd) _items.update { it + Item("new-${nextKey++}", jpeg, null) }
    }

    fun remove(key: String) {
        val item = _items.value.firstOrNull { it.key == key } ?: return
        item.attachment?.let(removed::add)
        _items.update { list -> list.filterNot { it.key == key } }
    }

    suspend fun bytes(item: Item): ByteArray = item.jpeg ?: documents.readAttachment(requireNotNull(item.attachment))

    /** Saves new photos to the encrypted vault under [ownerId] and drops the removed ones. */
    suspend fun commit(ownerId: String, ownerType: Attachment.OwnerType) {
        removed.forEach { documents.removeAttachment(it) }
        removed.clear()
        _items.value.filter { it.jpeg != null }.forEach { documents.addAttachment(ownerId, ownerType, it.jpeg!!, JPEG) }
    }

    companion object {
        const val MAX_PHOTOS = 4
        private const val IMAGE = "image/"
        private const val JPEG = "image/jpeg"
    }
}

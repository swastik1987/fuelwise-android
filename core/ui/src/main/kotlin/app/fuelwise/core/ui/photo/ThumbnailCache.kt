package app.fuelwise.core.ui.photo

import app.fuelwise.core.designsystem.component.FwPhoto

/**
 * Decodes each photo once for an [app.fuelwise.core.designsystem.component.FwPhotoStrip]; later lists reuse what's
 * decoded, by key. Blocking work: call from an injected IO dispatcher.
 */
class ThumbnailCache(private val maxPx: Int = DEFAULT_PX) {
    private val decoded = mutableMapOf<String, FwPhoto>()

    suspend fun <T> of(items: List<T>, key: (T) -> String, bytes: suspend (T) -> ByteArray): List<FwPhoto> =
        items.mapNotNull { item ->
            val k = key(item)
            decoded[k] ?: runCatching { PhotoImages.decode(bytes(item), maxPx) }.getOrNull()
                ?.let { FwPhoto(k, it) }
                ?.also { decoded[k] = it }
        }

    private companion object {
        const val DEFAULT_PX = 320
    }
}

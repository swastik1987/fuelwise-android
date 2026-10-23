// The platform ExifInterface, on purpose: see JpegShrinker.rotationDegrees.
@file:SuppressLint("ExifInterface")

package app.fuelwise.core.data.photo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reads a photo from the system photo picker (no storage permission, PRD §9) and shrinks it to an upright JPEG.
 * The bytes stay in memory until an entry is saved, then go to the encrypted vault (CLAUDE.md rule 5).
 */
class PickedPhotoReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) {
    /** Null when the picked file can't be read or isn't an image. */
    suspend fun read(uri: Uri, maxPx: Int = JpegShrinker.DEFAULT_MAX_PX): ByteArray? = withContext(io) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { JpegShrinker.shrink(it, maxPx) }
        }.getOrNull()
    }
}

/** Upright JPEGs no larger than a given size, for vehicle and entry photos (PRD F-01.1, F-02.1, F-04.1). */
object JpegShrinker {
    const val DEFAULT_MAX_PX = 1280
    private const val JPEG_QUALITY = 85
    private const val QUARTER = 90
    private const val HALF_TURN = 180

    /** Null if [bytes] isn't an image. Blocking: call from an IO dispatcher. */
    fun shrink(bytes: ByteArray, maxPx: Int = DEFAULT_MAX_PX, quality: Int = JPEG_QUALITY): ByteArray? {
        val sampled = decodeSampled(bytes, maxPx) ?: return null
        val scale = maxPx.toFloat() / maxOf(sampled.width, sampled.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            postRotate(rotationDegrees(bytes).toFloat())
        }
        val upright = Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
        return ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }

    private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
            },
        )
    }

    /**
     * Camera photos are often stored sideways with an EXIF rotation; the shrunk copy carries none, so apply it.
     * The platform ExifInterface: androidx.exifinterface is only in the APK through CameraX, and declaring it here is
     * a dependency change (CLAUDE.md rule 7). minSdk 26 has the platform parser fixes; only the user's own picked
     * photos are read.
     */
    private fun rotationDegrees(bytes: ByteArray): Int = runCatching {
        when (
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER
            ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
            ExifInterface.ORIENTATION_ROTATE_270 -> HALF_TURN + QUARTER
            else -> 0
        }
    }.getOrDefault(0)
}

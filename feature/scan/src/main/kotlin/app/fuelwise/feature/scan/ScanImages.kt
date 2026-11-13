package app.fuelwise.feature.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import app.fuelwise.core.domain.scan.GreyImage
import app.fuelwise.core.domain.scan.OcrBox
import app.fuelwise.core.domain.scan.OcrLine
import app.fuelwise.core.domain.scan.PixelRect
import com.google.mlkit.vision.text.Text
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Pixel plumbing between CameraX, core:domain's clean-ups and ML Kit (PRD F-15). Nothing here leaves the phone. */
internal object ScanImages {
    /** The camera image turned the way the user holds the phone. */
    fun upright(proxy: ImageProxy): Bitmap {
        val raw = proxy.toBitmap()
        val degrees = proxy.imageInfo.rotationDegrees
        if (degrees == 0) return raw
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    /** [bitmap] shrunk so its long side is at most [maxSide]; ML Kit gains nothing from more and slows down. */
    fun limit(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val factor = maxSide.toFloat() / side
        return bitmap.scale((bitmap.width * factor).roundToInt(), (bitmap.height * factor).roundToInt())
    }

    fun crop(bitmap: Bitmap, rect: PixelRect): Bitmap =
        Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width, rect.height)

    fun grey(bitmap: Bitmap): GreyImage {
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val luma = IntArray(argb.size) { i ->
            val c = argb[i]
            (RED * (c shr RED_SHIFT and BYTE) + GREEN * (c shr GREEN_SHIFT and BYTE) + BLUE * (c and BYTE)) / WEIGHTS
        }
        return GreyImage(bitmap.width, bitmap.height, luma)
    }

    fun bitmap(image: GreyImage): Bitmap {
        val argb = IntArray(image.pixels.size) { i ->
            val v = image.pixels[i]
            OPAQUE or (v shl RED_SHIFT) or (v shl GREEN_SHIFT) or v
        }
        return Bitmap.createBitmap(argb, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    /** ML Kit's lines with boxes moved from the cropped image back to the full image ([dx], [dy] = crop origin). */
    fun lines(text: Text, dx: Int, dy: Int): List<OcrLine> = text.textBlocks.flatMap { it.lines }.map { line ->
        OcrLine(line.text, line.boundingBox?.let { OcrBox(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) })
    }

    fun jpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }

    private const val JPEG_QUALITY = 80
    private const val BYTE = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val OPAQUE = -0x1000000

    // ITU-R BT.601 luma weights, out of 1000.
    private const val RED = 299
    private const val GREEN = 587
    private const val BLUE = 114
    private const val WEIGHTS = 1000
}

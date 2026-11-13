package app.fuelwise.core.domain.scan

import kotlin.math.roundToInt

/** A grey image: one luminance value (0 dark … 255 light) per pixel, row by row. */
class GreyImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixels must be width × height" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /** Mean luminance, 0–255. */
    fun mean(): Int = (pixels.sumOf { it.toLong() } / pixels.size).toInt()
}

/** A rectangle in image pixels; [right] and [bottom] are exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Where the on-screen guide sits in the view, as fractions of the view's width and height (PRD F-15). */
data class GuideRegion(val left: Float, val top: Float, val right: Float, val bottom: Float, val viewAspect: Float) {
    /**
     * The part of an upright [imageWidth] × [imageHeight] camera image under the guide, for a preview that fills the
     * view and crops the overflow (CameraX `FILL_CENTER`). Grown by [margin] of the guide's size on every side, since
     * people rarely frame the display exactly, then clamped to the image. Null for a degenerate guide.
     */
    fun cropOf(imageWidth: Int, imageHeight: Int, margin: Float = DEFAULT_MARGIN): PixelRect? {
        val emptyImage = imageWidth <= 0 || imageHeight <= 0
        val emptyGuide = viewAspect <= 0f || right <= left || bottom <= top
        if (emptyImage || emptyGuide) return null
        // The view shows the centre of the image at the view's aspect ratio.
        val imageAspect = imageWidth.toFloat() / imageHeight
        val (shownW, shownH) = if (imageAspect > viewAspect) {
            imageHeight * viewAspect to imageHeight.toFloat()
        } else {
            imageWidth.toFloat() to imageWidth / viewAspect
        }
        val offsetX = (imageWidth - shownW) / 2
        val offsetY = (imageHeight - shownH) / 2
        val growX = (right - left) * margin
        val growY = (bottom - top) * margin
        val rect = PixelRect(
            left = (offsetX + (left - growX) * shownW).roundToInt().coerceIn(0, imageWidth - 1),
            top = (offsetY + (top - growY) * shownH).roundToInt().coerceIn(0, imageHeight - 1),
            right = (offsetX + (right + growX) * shownW).roundToInt().coerceIn(1, imageWidth),
            bottom = (offsetY + (bottom + growY) * shownH).roundToInt().coerceIn(1, imageHeight),
        )
        return rect.takeIf { it.width > 0 && it.height > 0 }
    }

    private companion object {
        const val DEFAULT_MARGIN = 0.1f
    }
}

/**
 * Clean-ups that help text recognition read segment displays (PRD F-15). Pump LCDs show dark segments on a light,
 * often glary panel; LED displays show light segments on dark. Recognition misses digits whose segments don't touch
 * and digits on a washed-out panel, so the variants stretch the contrast and thicken the digit strokes until the
 * segments join. Pure Kotlin, so the camera layer only converts pixels in and out.
 */
object DisplayCleanup {
    /** How each variant is made. The camera reads the plain image too, so it is not listed here. */
    enum class Variant { Stretched, Thickened, ThickenedMore }

    fun apply(image: GreyImage, variant: Variant): GreyImage {
        val stretched = stretch(image)
        return when (variant) {
            Variant.Stretched -> stretched
            Variant.Thickened -> thicken(stretched, radius = 1)
            Variant.ThickenedMore -> thicken(stretched, radius = 2)
        }
    }

    /** Maps the 2nd–98th percentile luminance to 0–255, so glare and dim panels both gain contrast. */
    fun stretch(image: GreyImage): GreyImage {
        val histogram = IntArray(LEVELS)
        image.pixels.forEach { histogram[it.coerceIn(0, MAX_LEVEL)]++ }
        val low = percentile(histogram, image.pixels.size, LOW_PERCENTILE)
        val high = percentile(histogram, image.pixels.size, HIGH_PERCENTILE)
        if (high - low < MIN_SPREAD) return image
        val range = (high - low).toFloat()
        val out = IntArray(image.pixels.size) { i ->
            ((image.pixels[i] - low) * MAX_LEVEL / range).roundToInt().coerceIn(0, MAX_LEVEL)
        }
        return GreyImage(image.width, image.height, out)
    }

    /** True when the digits are darker than the panel (an LCD), judged from the panel's overall brightness. */
    fun darkDigits(image: GreyImage): Boolean = image.mean() >= MID_LEVEL

    /**
     * Grows the digit strokes by [radius] pixels in each direction, which joins segments separated by small gaps.
     * Dark digits grow with a minimum filter, light digits with a maximum filter.
     */
    fun thicken(image: GreyImage, radius: Int, darkDigits: Boolean = darkDigits(image)): GreyImage {
        if (radius <= 0) return image
        val pick: (Int, Int) -> Int = if (darkDigits) ::minOf else ::maxOf
        // A square filter is a horizontal pass followed by a vertical one.
        val across = pass(image, radius, pick, horizontal = true)
        return pass(across, radius, pick, horizontal = false)
    }

    /** Each pixel becomes the [pick] of its neighbours within [radius] along one direction. */
    private fun pass(image: GreyImage, radius: Int, pick: (Int, Int) -> Int, horizontal: Boolean): GreyImage {
        val w = image.width
        val h = image.height
        val out = IntArray(image.pixels.size) { i ->
            val x = i % w
            val y = i / w
            var v = image.pixels[i]
            for (d in -radius..radius) {
                val nx = if (horizontal) x + d else x
                val ny = if (horizontal) y else y + d
                if (nx in 0 until w && ny in 0 until h) v = pick(v, image[nx, ny])
            }
            v
        }
        return GreyImage(w, h, out)
    }

    private fun percentile(histogram: IntArray, count: Int, fraction: Float): Int {
        val target = (count * fraction).toLong()
        var seen = 0L
        histogram.forEachIndexed { level, n ->
            seen += n
            if (seen > target) return level
        }
        return MAX_LEVEL
    }

    private const val LEVELS = 256
    private const val MAX_LEVEL = 255
    private const val MID_LEVEL = 128
    private const val LOW_PERCENTILE = 0.02f
    private const val HIGH_PERCENTILE = 0.98f

    /** Below this spread the image is flat (a blank wall); stretching would only amplify noise. */
    private const val MIN_SPREAD = 24
}

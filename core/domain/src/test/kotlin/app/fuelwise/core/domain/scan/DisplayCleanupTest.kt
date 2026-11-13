package app.fuelwise.core.domain.scan

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DisplayCleanupTest {
    /** Rows of '#' (dark) and '.' (light) as a grey image. */
    private fun image(vararg rows: String, dark: Int = 40, light: Int = 200) = GreyImage(
        rows.first().length,
        rows.size,
        rows.joinToString("").map { if (it == '#') dark else light }.toIntArray(),
    )

    private fun GreyImage.rows(): List<String> = (0 until height).map { y ->
        (0 until width).joinToString("") { x -> if (this[x, y] < 128) "#" else "." }
    }

    @Test
    fun `stretch spreads a washed-out panel over the full range`() {
        val glary = GreyImage(4, 25, IntArray(100) { if (it % 2 == 0) 170 else 230 })
        val out = DisplayCleanup.stretch(glary)
        assertThat(out.pixels.min()).isEqualTo(0)
        assertThat(out.pixels.max()).isEqualTo(255)
    }

    @Test
    fun `stretch leaves a flat image alone`() {
        val flat = GreyImage(3, 3, IntArray(9) { 120 + it })
        assertThat(DisplayCleanup.stretch(flat).pixels).isEqualTo(flat.pixels)
    }

    @Test
    fun `thickening joins dark segments split by a one-pixel gap on an LCD`() {
        // A vertical stroke whose two segments don't touch, as on a 7-segment "1".
        val split = image(
            ".....",
            "..#..",
            "..#..",
            ".....",
            "..#..",
            "..#..",
            ".....",
        )
        assertThat(DisplayCleanup.darkDigits(split)).isTrue()
        val joined = DisplayCleanup.thicken(split, radius = 1)
        assertThat(joined.rows()[3]).isEqualTo(".###.")
    }

    @Test
    fun `thickening grows light segments on an LED display`() {
        val led = image(
            "#####",
            "##.##",
            "#####",
            "##.##",
            "#####",
        )
        assertThat(DisplayCleanup.darkDigits(led)).isFalse()
        val joined = DisplayCleanup.thicken(led, radius = 1)
        assertThat(joined.rows()[2]).isEqualTo("#...#")
    }

    @Test
    fun `the guide maps to the part of the image the preview shows`() {
        // A 720 × 1280 upright frame shown in a taller 9:20 view: the view crops the frame's sides.
        val guide = GuideRegion(left = 0.1f, top = 0.25f, right = 0.9f, bottom = 0.75f, viewAspect = 9f / 20f)
        val rect = guide.cropOf(720, 1280, margin = 0f)!!
        // Shown width = 1280 × 9/20 = 576 px, offset (720 − 576) / 2 = 72 px.
        assertThat(rect).isEqualTo(PixelRect(left = 130, top = 320, right = 590, bottom = 960))
    }

    @Test
    fun `the crop grows by the margin and stays inside the image`() {
        val guide = GuideRegion(left = 0f, top = 0f, right = 1f, bottom = 0.5f, viewAspect = 720f / 1280f)
        val rect = guide.cropOf(720, 1280)!!
        assertThat(rect).isEqualTo(PixelRect(left = 0, top = 0, right = 720, bottom = 704))
    }

    @Test
    fun `a degenerate guide crops nothing`() {
        assertThat(GuideRegion(0.5f, 0.5f, 0.5f, 0.9f, 1f).cropOf(100, 100)).isNull()
    }

    @Test
    fun `the reading that checks out wins over one with more fields`() {
        data class R(val valid: Boolean, val fields: Int)
        val best = ScanChoice.best(listOf(R(false, 3), R(true, 3), R(false, 2)), { it.valid }, { it.fields })
        assertThat(best).isEqualTo(R(true, 3))
        assertThat(ScanChoice.best(listOf(R(false, 1), R(false, 2)), { it.valid }, { it.fields }))
            .isEqualTo(R(false, 2))
        assertThat(ScanChoice.best(listOf(R(false, 0)), { it.valid }, { it.fields })).isNull()
    }

    @Test
    fun `two partial reads of a photo combine into one that checks out`() {
        // One clean-up read the amount and litres, another the rate.
        val reads = listOf(
            ScanFixtures.lines("2887.14", "29.32", "Litre"),
            ScanFixtures.lines("Density 821.4", "98.47 ₹/Litre"),
        )
        val readings = ScanChoice.candidates(reads).map { PumpMeterParser.parse(it) }
        val best = ScanChoice.best(
            readings,
            { it.validated },
            { listOfNotNull(it.total, it.quantity, it.unitPrice).size },
        )
        assertThat(best?.validated).isTrue()
        assertThat(best?.unitPrice?.value?.paise).isEqualTo(9_847)
    }
}

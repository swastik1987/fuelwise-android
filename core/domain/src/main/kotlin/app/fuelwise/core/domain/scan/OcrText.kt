package app.fuelwise.core.domain.scan

import kotlin.math.max
import kotlin.math.min

/** A rectangle in image pixels, as the on-device text recogniser reports it. */
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
    val centerY: Int get() = (top + bottom) / 2

    fun union(other: OcrBox) = OcrBox(
        min(left, other.left),
        min(top, other.top),
        max(right, other.right),
        max(bottom, other.bottom),
    )
}

/**
 * One line of recognised text (PRD F-15). The camera layer maps ML Kit's lines to this, so every parser here is pure
 * Kotlin and testable with text fixtures. [box] is null in fixtures that don't care about layout.
 */
data class OcrLine(val text: String, val box: OcrBox? = null)

/** A value read from the image and where it was, for the live labels on the camera view (design.md screen 9). */
data class Scanned<T>(val value: T, val box: OcrBox?)

/** A visual row: recogniser lines that sit side by side ("RATE" and "102.92" are often separate lines). */
internal data class OcrRow(val index: Int, val text: String, val box: OcrBox?)

internal object OcrRows {
    /**
     * Groups lines into rows by vertical overlap, top to bottom, each row read left to right. Lines without a box keep
     * their own row in input order.
     */
    fun of(lines: List<OcrLine>): List<OcrRow> {
        val clean = lines.filter { it.text.isNotBlank() }
        val boxed = clean.mapNotNull { line -> line.box?.let { line.text.trim() to it } }
        if (boxed.size != clean.size) {
            return clean.mapIndexed { i, line -> OcrRow(i, line.text.trim(), line.box) }
        }
        val rows = mutableListOf<MutableList<Pair<String, OcrBox>>>()
        boxed.sortedBy { it.second.top }.forEach { line ->
            val row = rows.lastOrNull()?.takeIf { sameRow(it, line.second) }
            if (row != null) row += line else rows += mutableListOf(line)
        }
        return rows.mapIndexed { i, row ->
            val sorted = row.sortedBy { it.second.left }
            OcrRow(
                index = i,
                text = sorted.joinToString(" ") { it.first },
                box = sorted.map { it.second }.reduce(OcrBox::union),
            )
        }
    }

    private fun sameRow(row: List<Pair<String, OcrBox>>, box: OcrBox): Boolean {
        val rowBox = row.map { it.second }.reduce(OcrBox::union)
        val overlap = min(rowBox.bottom, box.bottom) - max(rowBox.top, box.top)
        val smaller = min(rowBox.height, box.height).coerceAtLeast(1)
        return overlap * 2 >= smaller
    }
}

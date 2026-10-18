package app.fuelwise.core.domain.importing

import app.fuelwise.core.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.LocalTime

/** How to read an ambiguous date like 03/04/2026. India writes day first; some apps export month first. */
enum class DateOrder { DayMonthYear, MonthDayYear }

/**
 * Lenient readers for values typed by people or written by other apps: Indian and European digit grouping,
 * currency symbols, many date layouts and Excel's serial dates. Each returns null when the value can't be read.
 */
object ImportValues {
    /** "₹1,23,456.78", "Rs 450", "1.234,5", "12,5" and Excel numbers. Negative numbers are rejected. */
    fun number(cell: Cell): BigDecimal? = when (cell) {
        is Cell.Number -> if (cell.value.isFinite() && cell.value >= 0) BigDecimal.valueOf(cell.value) else null
        is Cell.Text -> number(cell.value)
        Cell.Empty -> null
    }

    fun number(text: String): BigDecimal? {
        val cleaned = text.trim().lowercase()
            .removePrefix("inr").removePrefix("rs.").removePrefix("rs")
            .filterNot { it in IGNORED_CHARS }
        if (!NUMBER_CHARS.matches(cleaned)) return null
        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')
        val normal = when {
            lastDot >= 0 && lastComma >= 0 ->
                // Whichever comes last is the decimal mark: 1,23,456.78 or 1.234,56.
                if (lastDot > lastComma) cleaned.replace(",", "") else cleaned.replace(".", "").replace(',', '.')

            lastComma >= 0 -> commaOnly(cleaned)

            else -> cleaned
        }
        return normal.takeIf { n -> n.count { it == '.' } <= 1 }?.toBigDecimalOrNull()
    }

    fun double(cell: Cell): Double? = number(cell)?.toDouble()

    fun money(cell: Cell): Money? = number(cell)?.let {
        Money(it.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong())
    }

    /** Yes/no from the vocabulary; null when blank or not recognised. */
    fun flag(cell: Cell, vocabulary: ImportVocabulary): Boolean? {
        if (cell is Cell.Number) return cell.value != 0.0
        return when (ImportVocabulary.normalize(cell.asText())) {
            in vocabulary.yes -> true
            in vocabulary.no -> false
            else -> null
        }
    }

    /**
     * A date and, when present, a time: Excel serials (with a fraction for the time), ISO "2026-10-16 09:30",
     * "16/10/2026", "16-10-26", "16.10.2026", "16 Oct 2026", "Oct 16, 2026" and 12-hour times.
     */
    fun dateTime(cell: Cell, order: DateOrder): LocalDateTime? = when (cell) {
        is Cell.Number -> DateParsing.fromSerial(cell.value)
        is Cell.Text -> DateParsing.parse(cell.value.trim(), order)
        Cell.Empty -> null
    }

    /** A time on its own: an Excel fraction of a day or "9:30", "09:30:15", "9:30 pm". */
    fun time(cell: Cell): LocalTime? = when (cell) {
        is Cell.Number -> if (cell.value in 0.0..1.0) DateParsing.fromSerial(cell.value + 1)?.toLocalTime() else null
        is Cell.Text -> DateParsing.parseTime(cell.value.trim())
        Cell.Empty -> null
    }

    /** The order that makes every date in the column valid, if the column decides it; null when all could be either. */
    fun detectDateOrder(cells: List<Cell>): DateOrder? =
        cells.asSequence().mapNotNull { (it as? Cell.Text)?.value }.mapNotNull(DateParsing::decidedOrder).firstOrNull()

    /** Commas alone are grouping when every group after the first has 2 or 3 digits ending in 3; else decimal. */
    private fun commaOnly(text: String): String {
        val groups = text.split(',')
        val grouping = groups.size > 1 && groups.last().length == GROUP && groups.drop(1).all { it.length in 2..GROUP }
        return when {
            grouping -> text.replace(",", "")
            groups.size == 2 -> text.replace(',', '.')
            else -> text
        }
    }

    /** Currency marks, spaces (plain and no-break) and apostrophe grouping, all dropped before reading. */
    private val IGNORED_CHARS = "₹ '" + Char(NO_BREAK_SPACE)
    private const val NO_BREAK_SPACE = 0x00A0
    private val NUMBER_CHARS = Regex("[0-9.,]+")
    private const val GROUP = 3
}

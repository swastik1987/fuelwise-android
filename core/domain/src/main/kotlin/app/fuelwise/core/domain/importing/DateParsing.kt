package app.fuelwise.core.domain.importing

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The date and time layouts [ImportValues] understands. */
internal object DateParsing {
    fun fromSerial(serial: Double): LocalDateTime? {
        if (!serial.isFinite() || serial < 1 || serial > MAX_SERIAL) return null
        val days = Math.floor(serial).toLong()
        val seconds = Math.round((serial - days) * SECONDS_PER_DAY)
        // Excel's day 0 is 1899-12-30 once its fictional 29 Feb 1900 is accounted for.
        return EXCEL_EPOCH.plusDays(days).atStartOfDay().plusSeconds(seconds)
    }

    fun parse(text: String, order: DateOrder): LocalDateTime? {
        if (text.isEmpty()) return null
        val serial = text.toDoubleOrNull()
        val split = text.replace('T', ' ').split(' ', limit = 2)
        return when {
            serial != null -> fromSerial(serial)

            else -> numericDate(split[0], order)?.atTime(split.getOrNull(1)?.let(::parseTime) ?: LocalTime.MIDNIGHT)
                ?: namedMonthDate(text)?.atStartOfDay()
        }
    }

    /** The order a day-first or month-first date forces ("25/04" is day first); null when either reading works. */
    fun decidedOrder(text: String): DateOrder? {
        val parts = text.trim().substringBefore(' ').split('/', '-', '.')
        val numbers = parts.mapNotNull { it.toIntOrNull() }
        val decides = parts.size == DATE_PARTS && numbers.size == DATE_PARTS && parts[0].length != YEAR_DIGITS
        return when {
            !decides -> null
            numbers[0] > MONTHS -> DateOrder.DayMonthYear
            numbers[1] > MONTHS -> DateOrder.MonthDayYear
            else -> null
        }
    }

    private fun numericDate(text: String, order: DateOrder): LocalDate? {
        val raw = text.split('/', '-', '.')
        val parts = raw.mapNotNull { it.toIntOrNull() }
        if (raw.size != DATE_PARTS || parts.size != DATE_PARTS) return null
        val (a, b, c) = parts
        val ymd = raw[0].length == YEAR_DIGITS
        val dayFirst = when {
            ymd -> false
            a > MONTHS -> true
            b > MONTHS -> false
            else -> order == DateOrder.DayMonthYear
        }
        return when {
            ymd -> safeDate(a, b, c)
            dayFirst -> safeDate(fullYear(c), b, a)
            else -> safeDate(fullYear(c), a, b)
        }
    }

    /** "16 Oct 2026", "16-Oct-26", "Oct 16, 2026", "16 October 2026 9:30". */
    private fun namedMonthDate(text: String): LocalDate? {
        val tokens = text.replace(',', ' ').replace('-', ' ').split(' ').filter { it.isNotBlank() }
        val monthAt = tokens.indexOfFirst { month(it) != null }
        val numbers = tokens.filterIndexed { i, _ -> i != monthAt }.mapNotNull { it.toIntOrNull() }
        if (monthAt < 0 || numbers.size < 2) return null
        val (first, second) = numbers
        val (day, year) = if (first > DAYS_MAX) second to first else first to second
        return month(tokens[monthAt])?.let { safeDate(fullYear(year), it, day) }
    }

    private fun month(token: String): Int? {
        val t = token.lowercase().take(MONTH_ABBREVIATION)
        return MONTH_NAMES.indexOf(t).takeIf { it >= 0 }?.plus(1)
    }

    fun parseTime(text: String): LocalTime? {
        val lower = text.lowercase().replace(".", "").trim()
        val pm = lower.endsWith("pm")
        val am = lower.endsWith("am")
        val raw = lower.removeSuffix("pm").removeSuffix("am").trim().split(':')
        val parts = raw.mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != raw.size || parts.size !in 2..TIME_PARTS) return null
        var hour = parts[0]
        if (pm && hour < NOON) hour += NOON
        if (am && hour == NOON) hour = 0
        val minute = parts[1]
        val second = parts.getOrElse(2) { 0 }
        val valid = hour in 0..LAST_HOUR && minute in 0..LAST_MINUTE && second in 0..LAST_MINUTE
        return if (valid) LocalTime.of(hour, minute, second) else null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()?.takeIf { it.year in MIN_YEAR..MAX_YEAR }

    private fun fullYear(year: Int) = if (year < CENTURY) TWO_THOUSAND + year else year

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val DATE_PARTS = 3
    private const val TIME_PARTS = 3
    private const val YEAR_DIGITS = 4
    private const val MONTHS = 12
    private const val DAYS_MAX = 31
    private const val MONTH_ABBREVIATION = 3
    private const val NOON = 12
    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
    private const val SECONDS_PER_DAY = 86_400.0
    private const val MAX_SERIAL = 2_958_465.0
    private const val CENTURY = 100
    private const val TWO_THOUSAND = 2000
    private const val MIN_YEAR = 1980
    private const val MAX_YEAR = 2100
}

package app.fuelwise.core.domain.scan

import app.fuelwise.core.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What a fuel receipt scan found (PRD F-15). Every field is optional and confirmed by the user before use. */
data class ReceiptReading(
    val amounts: TriangleReading = TriangleReading(),
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val stationName: String? = null,
) {
    val isEmpty: Boolean get() = amounts.isEmpty && date == null && stationName == null

    /**
     * When the fill happened, in UTC ms. Without a printed time, today's receipt keeps the current time and an older
     * one gets noon. A date in the future is a misread and gives null.
     */
    fun dateTimeUtcMs(zone: ZoneId, nowUtcMs: Long): Long? {
        val day = date ?: return null
        val now = Instant.ofEpochMilli(nowUtcMs).atZone(zone)
        val at = when {
            time != null -> day.atTime(time).atZone(zone)
            day == now.toLocalDate() -> now
            else -> day.atTime(LocalTime.NOON).atZone(zone)
        }
        return at.toInstant().toEpochMilli().takeIf { it <= nowUtcMs }
    }
}

/** Receipt scan (PRD F-15): date, total, quantity, rate and station name from a printed fuel receipt. */
object ReceiptParser {
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val NUMERIC_DATE = Regex("""(?<!\d)(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})(?!\d)""")
    private val ISO_DATE = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
    private val NAMED_DATE =
        Regex("""(?<!\d)(\d{1,2})[\s-]*(${MONTHS.joinToString("|")})[A-Z]*[\s,-]*(\d{4}|\d{2})(?!\d)""")
    private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?""")
    private val DATE_LABEL = Regex("(?<![A-Z])(DATE|DT)(?![A-Z])")

    /** Oil-company and CNG-distributor names printed at the top of Indian receipts. */
    private val BRANDS = Regex(
        "(?<![A-Z])(INDIAN ?OIL|IOCL?|BHARAT ?PETROLEUM|BPCL|HINDUSTAN ?PETROLEUM|HPCL|HP|SHELL|NAYARA|ESSAR|" +
            "JIO[- ]?BP|RELIANCE|IGL|MGL|MNGL|ADANI|GAIL|TORRENT|GUJARAT ?GAS)(?![A-Z])",
    )

    /** Words that mark the dealer's own name ("Shree Balaji Fuels", "HP Service Station"). */
    private val OUTLET = Regex(
        "(?<![A-Z])(FUELS?|PETROL|PETROLEUM|SERVICES?|FILLING|STATION|PUMP|AGENC[A-Z]*|AUTOMOBILES|KENDRA|" +
            "ENTERPRISES?|TRADERS|CNG)(?![A-Z])",
    )

    /** Header lines that are not a name. */
    private val SKIP = Regex(
        "(?<![A-Z])(WELCOME|TAX|INVOICE|RECEIPT|BILL|CASH|MEMO|ORIGINAL|DUPLICATE|COPY|CUSTOMER|GST|GSTIN|TIN|" +
            "TEL|PH|PHONE|MOB|DATE|TIME|THANK|THANKS|VISIT|NOZZLE|PRODUCT|RATE|VOLUME|AMOUNT|TOTAL|VEHICLE)" +
            "(?![A-Z])",
    )
    private const val HEADER_ROWS = 6
    private const val MIN_LETTERS = 3
    private const val MIN_LETTER_SHARE = 0.6
    private const val MAX_NAME = 60
    private const val ACRONYM_MAX = 4
    private const val CENTURY = 2000
    private const val HOURS_HALF_DAY = 12
    private const val MONTHS_IN_YEAR = 12

    fun parse(lines: List<OcrLine>, rateHint: Money? = null, tuning: ScanTuning = ScanTuning()): ReceiptReading {
        val rows = OcrRows.of(lines)
        val upper = rows.map { it.text.uppercase() }
        val dateRow = upper.firstOrNull { DATE_LABEL.containsMatchIn(it) && date(it) != null }
            ?: upper.firstOrNull { date(it) != null }
        return ReceiptReading(
            amounts = TriangleReader(tuning, TriangleReader.Layout.Receipt).read(rows, rateHint),
            date = dateRow?.let(::date),
            time = (listOfNotNull(dateRow) + upper).firstNotNullOfOrNull(::time),
            stationName = stationName(upper),
        )
    }

    private fun date(text: String): LocalDate? {
        ISO_DATE.find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            return safeDate(y.toInt(), mo.toInt(), d.toInt())
        }
        NAMED_DATE.find(text)?.let { m ->
            val (d, mon, y) = m.destructured
            return safeDate(fullYear(y), MONTHS.indexOf(mon) + 1, d.toInt())
        }
        return NUMERIC_DATE.find(text)?.let { m ->
            val (a, b, y) = m.destructured.toList().map { it.toInt() }
            // India writes day first; a month above 12 means the receipt printer is set to month first.
            if (b > MONTHS_IN_YEAR && a <= MONTHS_IN_YEAR) {
                safeDate(fullYear(m.groupValues[3]), a, b)
            } else {
                safeDate(fullYear(m.groupValues[3]), b, a)
            }
        }
    }

    private fun time(text: String): LocalTime? {
        val m = TIME.find(text) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val second = m.groupValues[3].toIntOrNull() ?: 0
        when (m.groupValues[4]) {
            "PM" -> if (hour < HOURS_HALF_DAY) hour += HOURS_HALF_DAY
            "AM" -> if (hour == HOURS_HALF_DAY) hour = 0
        }
        return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
    }

    private fun fullYear(text: String): Int = text.toInt().let { if (text.length == 2) CENTURY + it else it }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    /** The dealer's name from the header: an outlet line, else a brand line, else the first name-like one. */
    private fun stationName(upper: List<String>): String? {
        val header = upper.take(HEADER_ROWS).filter(::looksLikeName)
        // "BHARAT PETROLEUM" over "SAI BABA AUTOMOBILES" would name every BPCL pump the same.
        val pick = header.firstOrNull { OUTLET.containsMatchIn(it) && !BRANDS.containsMatchIn(it) }
            ?: header.firstOrNull { OUTLET.containsMatchIn(it) }
            ?: header.firstOrNull { BRANDS.containsMatchIn(it) }
            ?: header.firstOrNull()
        return pick?.let(::tidy)
    }

    private fun looksLikeName(text: String): Boolean {
        val chars = text.filterNot { it.isWhitespace() }
        val letters = chars.count { it.isLetter() }
        return letters >= MIN_LETTERS && letters >= chars.length * MIN_LETTER_SHARE && !SKIP.containsMatchIn(text)
    }

    /** "SHREE BALAJI FUELS" → "Shree Balaji Fuels"; short all-caps words stay ("HP", "IOCL"). */
    private fun tidy(text: String): String = text
        .replace(Regex("[^A-Z0-9&.,'()\\- ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            if (word.length <= ACRONYM_MAX && BRANDS.matches(word)) {
                word
            } else {
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
        .trim(' ', '-', ',', '.')
        .take(MAX_NAME)
}

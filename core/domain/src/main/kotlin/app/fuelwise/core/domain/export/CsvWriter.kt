package app.fuelwise.core.domain.export

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * RFC 4180 CSV for exports (PRD F-13.1): comma-separated, CRLF line ends, a field quoted only when it holds a comma,
 * a quote or a line break. Starts with a UTF-8 byte-order mark so Excel shows ₹ and Hindi correctly; the importer's
 * [app.fuelwise.core.domain.importing.Csv] drops it again.
 */
object CsvWriter {
    fun write(header: List<String>, rows: List<List<String?>>): String = buildString {
        append(BOM)
        line(header)
        rows.forEach { line(it) }
    }

    private fun StringBuilder.line(values: List<String?>) {
        values.joinTo(this, ",") { field(it.orEmpty()) }
        append("\r\n")
    }

    internal fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private val BOM = Char(BYTE_ORDER_MARK).toString()
    private const val BYTE_ORDER_MARK = 0xFEFF
}

/**
 * How values look in an exported CSV: machine-readable and the same in every language. Money is rupees with two
 * decimals worked out from paise with integer arithmetic (CLAUDE.md rule 1); quantities and distances are canonical
 * units with a dot as the decimal mark; dates are ISO.
 */
object CsvValues {
    /** 123456 paise → "1234.56"; −5 → "-0.05". No grouping and no ₹ sign, so spreadsheets read it as a number. */
    fun rupees(paise: Long): String {
        val whole = paise / PAISE_PER_RUPEE
        val fraction = abs(paise % PAISE_PER_RUPEE).toString().padStart(2, '0')
        val sign = if (paise < 0 && whole == 0L) "-" else ""
        return "$sign$whole.$fraction"
    }

    /** Rounded half-up to [scale] decimals, trailing zeros dropped: 35.2, 42318, 0.125. */
    fun decimal(value: Double, scale: Int = 2): String =
        BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun flag(value: Boolean): String = if (value) "true" else "false"

    /** yyyy-MM-dd in the entry's own zone. */
    fun date(utcMs: Long, zone: ZoneId): String = DATE.format(Instant.ofEpochMilli(utcMs).atZone(zone))

    /** HH:mm (24-hour) in the entry's own zone. */
    fun time(utcMs: Long, zone: ZoneId): String = TIME.format(Instant.ofEpochMilli(utcMs).atZone(zone))

    /**
     * Text the user typed. A leading `=`, `+`, `-` or `@` would make Excel run it as a formula (CSV injection), so
     * such text gets a leading apostrophe, which spreadsheets hide.
     */
    fun text(value: String?): String? = value?.let { if (it.firstOrNull() in FORMULA_STARTS) "'$it" else it }

    /** A stored zone id, or [fallback] when it's missing or unknown. */
    fun zone(zoneId: String?, fallback: ZoneId): ZoneId =
        zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback

    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
    private const val PAISE_PER_RUPEE = 100L
}

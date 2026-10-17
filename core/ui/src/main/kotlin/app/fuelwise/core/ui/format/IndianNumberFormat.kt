package app.fuelwise.core.ui.format

import app.fuelwise.core.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * The only place money and quantities become text (CLAUDE.md rule 1). Indian digit grouping:
 * the last three digits, then groups of two — ₹1,23,456.78. Written by hand so output never depends
 * on the device's ICU version or locale settings.
 */
object IndianNumberFormat {
    /** ₹1,23,456.78 — always two decimals. */
    fun rupees(money: Money): String = rupeesInternal(money.paise, showPaise = true)

    /** ₹1,23,457 — rounded half-up to whole rupees, for summaries and tiles. */
    fun rupeesWhole(money: Money): String = rupeesInternal(money.paise, showPaise = false)

    /** Groups an integer the Indian way: 1234567 → "12,34,567". */
    fun group(value: Long): String {
        val digits = abs(value).toString()
        val grouped = if (digits.length <= LAST_GROUP) {
            digits
        } else {
            val head = digits.dropLast(LAST_GROUP)
            val tail = digits.takeLast(LAST_GROUP)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
        }
        return if (value < 0) "-$grouped" else grouped
    }

    /** A decimal with Indian grouping and a fixed number of decimals, rounded half-up: 42318.456 → "42,318.5". */
    fun decimal(value: Double, decimals: Int): String {
        require(decimals >= 0) { "decimals must be ≥ 0" }
        val rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
        val integerPart = rounded.toBigInteger().toLong()
        val sign = if (rounded.signum() < 0 && integerPart == 0L) "-" else ""
        val fraction = rounded.abs().toPlainString().substringAfter('.', "")
        return sign + group(integerPart) + if (decimals > 0) ".$fraction" else ""
    }

    /** "16 Oct 2026". Month names follow [locale] (English by default). */
    fun date(date: LocalDate, locale: Locale = Locale.ENGLISH): String =
        DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date)

    private fun rupeesInternal(paise: Long, showPaise: Boolean): String {
        val negative = paise < 0
        val magnitude = abs(paise)
        val body = if (showPaise) {
            group(magnitude / Money.PAISE_PER_RUPEE) + "." +
                (magnitude % Money.PAISE_PER_RUPEE).toString().padStart(2, '0')
        } else {
            group((magnitude + HALF_RUPEE) / Money.PAISE_PER_RUPEE)
        }
        return (if (negative) "−" else "") + RUPEE + body
    }

    private const val LAST_GROUP = 3
    private const val HALF_RUPEE = 50L
    private const val RUPEE = "₹"
}

package app.fuelwise.core.ui.format

import app.fuelwise.core.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/** Rupee text typed on the keypad ⇄ [Money]. Typed text is plain ("3334.6"); display adds grouping. */
object MoneyInput {
    /** "3334.61" → ₹3,334.61 as paise. Null for empty or malformed text. */
    fun parse(text: String): Money? {
        val value = NumberInput.parseDecimal(text) ?: return null
        val paise = BigDecimal.valueOf(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        return Money(paise.longValueExact())
    }

    /** ₹3,334.61 → "3334.61"; whole rupees drop the decimals ("2000"). For seeding a field from a stored value. */
    fun plain(money: Money): String {
        val rupees = money.paise / Money.PAISE_PER_RUPEE
        val paise = money.paise % Money.PAISE_PER_RUPEE
        return if (paise == 0L) rupees.toString() else "$rupees.${paise.toString().padStart(2, '0')}"
    }

    /** Groups the integer part of typed text, leaving what follows the dot as typed: "123456.7" → "1,23,456.7". */
    fun groupTyped(text: String): String {
        if (text.isEmpty()) return text
        val integer = text.substringBefore('.')
        val rest = if ('.' in text) "." + text.substringAfter('.') else ""
        val grouped = integer.toLongOrNull()?.let(IndianNumberFormat::group) ?: integer
        return grouped + rest
    }
}

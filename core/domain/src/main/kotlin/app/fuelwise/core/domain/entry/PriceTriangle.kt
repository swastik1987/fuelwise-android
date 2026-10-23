package app.fuelwise.core.domain.entry

import app.fuelwise.core.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The price triangle (PRD F-02.2): the user types any two of quantity, price per unit and total; the third is
 * computed. The two most recently edited fields are "locked"; the other one is derived. Quantities round to 2 dp,
 * money to the paisa.
 */
object PriceTriangle {
    enum class Field { Quantity, UnitPrice, Total }

    data class Values(val quantity: Double?, val unitPrice: Money?, val total: Money?)

    /** Edit history, most recent first. The computed field is whichever is not among the last two edits. */
    fun computedField(recentEdits: List<Field>): Field {
        val locked = recentEdits.distinct().take(2)
        return Field.entries.firstOrNull { it !in locked } ?: Field.Total
    }

    /** Records an edit of [field], keeping the two most recent distinct edits. */
    fun recordEdit(recentEdits: List<Field>, field: Field): List<Field> =
        (listOf(field) + recentEdits).distinct().take(2)

    /**
     * Fills in [computed] from the other two values. Returns the input unchanged (with the computed value cleared)
     * when an input is missing or zero, so a half-typed form never shows a stale or infinite number.
     */
    fun solve(values: Values, computed: Field): Values = when (computed) {
        Field.Total -> values.copy(total = totalOf(values.quantity, values.unitPrice))
        Field.UnitPrice -> values.copy(unitPrice = unitPriceOf(values.quantity, values.total))
        Field.Quantity -> values.copy(quantity = quantityOf(values.total, values.unitPrice))
    }

    fun totalOf(quantity: Double?, unitPrice: Money?): Money? {
        if (quantity == null || unitPrice == null || quantity <= 0.0) return null
        val paise = BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(unitPrice.paise))
        return Money(paise.setScale(0, RoundingMode.HALF_UP).longValueExact())
    }

    fun unitPriceOf(quantity: Double?, total: Money?): Money? {
        if (quantity == null || total == null || quantity <= 0.0) return null
        val paise = BigDecimal.valueOf(total.paise).divide(BigDecimal.valueOf(quantity), 0, RoundingMode.HALF_UP)
        return Money(paise.longValueExact())
    }

    fun quantityOf(total: Money?, unitPrice: Money?): Double? {
        if (total == null || unitPrice == null || unitPrice.paise <= 0) return null
        return BigDecimal.valueOf(total.paise)
            .divide(BigDecimal.valueOf(unitPrice.paise), QUANTITY_DECIMALS, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * A discount typed as a percentage of the total (PRD F-02.2: "discount (₹ or %)"), in paise, rounded half-up.
     * Null when either is missing or the percentage is outside 0–100. Stored as rupees like any other discount.
     */
    fun discountOf(total: Money?, percent: Double?): Money? {
        if (total == null || percent == null || percent !in 0.0..MAX_PERCENT) return null
        val paise = BigDecimal.valueOf(total.paise).multiply(BigDecimal.valueOf(percent))
            .divide(BigDecimal.valueOf(MAX_PERCENT), 0, RoundingMode.HALF_UP)
        return Money(paise.longValueExact())
    }

    const val QUANTITY_DECIMALS = 2
    private const val MAX_PERCENT = 100.0
}

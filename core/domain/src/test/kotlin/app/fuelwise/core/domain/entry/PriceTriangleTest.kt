package app.fuelwise.core.domain.entry

import app.fuelwise.core.domain.entry.PriceTriangle.Field.Quantity
import app.fuelwise.core.domain.entry.PriceTriangle.Field.Total
import app.fuelwise.core.domain.entry.PriceTriangle.Field.UnitPrice
import app.fuelwise.core.model.Money
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PriceTriangleTest {
    @Test
    fun `total from quantity and price - design mock values`() {
        // 32.40 L × ₹102.92 = ₹3,334.608 → ₹3,334.61
        assertThat(PriceTriangle.totalOf(32.40, Money(10_292))).isEqualTo(Money(333_461))
    }

    @Test
    fun `price from quantity and total`() {
        // ₹3,397.89 ÷ 38.20 L = ₹88.95/L (design screen 35)
        assertThat(PriceTriangle.unitPriceOf(38.20, Money(339_789))).isEqualTo(Money(8_895))
    }

    @Test
    fun `quantity from total and price rounds to 2 dp`() {
        // Pump scan example: ₹2,000 at ₹102.92/L = 19.4326… → 19.43 L
        assertThat(PriceTriangle.quantityOf(Money(200_000), Money(10_292))).isEqualTo(19.43)
    }

    @Test
    fun `missing or zero inputs compute nothing`() {
        assertThat(PriceTriangle.totalOf(null, Money(10_292))).isNull()
        assertThat(PriceTriangle.unitPriceOf(0.0, Money(1_000))).isNull()
        assertThat(PriceTriangle.quantityOf(Money(1_000), Money(0))).isNull()
    }

    @Test
    fun `the two most recent edits are locked and the third is computed`() {
        var edits = emptyList<PriceTriangle.Field>()
        // Nothing typed yet: any field may end up computed.
        assertThat(PriceTriangle.computedField(edits)).isEqualTo(Quantity)
        edits = PriceTriangle.recordEdit(edits, Quantity)
        edits = PriceTriangle.recordEdit(edits, UnitPrice)
        assertThat(PriceTriangle.computedField(edits)).isEqualTo(Total)
        edits = PriceTriangle.recordEdit(edits, Total) // now Total and UnitPrice are locked
        assertThat(PriceTriangle.computedField(edits)).isEqualTo(Quantity)
        edits = PriceTriangle.recordEdit(edits, Total) // re-editing the same field doesn't change the lock
        assertThat(PriceTriangle.computedField(edits)).isEqualTo(Quantity)
    }

    @Test
    fun `solve fills only the computed field`() {
        val solved = PriceTriangle.solve(PriceTriangle.Values(19.43, Money(10_292), null), Total)
        assertThat(solved.total).isEqualTo(Money(199_974))
        assertThat(solved.quantity).isEqualTo(19.43)
    }
}

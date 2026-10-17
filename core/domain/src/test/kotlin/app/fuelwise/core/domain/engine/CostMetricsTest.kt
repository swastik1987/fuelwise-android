package app.fuelwise.core.domain.engine

import app.fuelwise.core.model.Money
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CostMetricsTest {
    private val compute = ComputeCostMetrics()
    private val october = Period(day(2026, 10, 1), day(2026, 11, 1))

    @Test
    fun `cost per km uses energy plus included expenses only`() {
        val spends = listOf(
            Spend(Money(333_461), day(2026, 10, 2), Spend.Kind.Energy), // ₹3,334.61 petrol
            Spend(Money(290_539), day(2026, 10, 20), Spend.Kind.Energy),
            Spend(Money(485_000), day(2026, 10, 8), Spend.Kind.Expense), // service
            Spend(Money(1_200_000), day(2026, 10, 5), Spend.Kind.Expense, includeInCostPerKm = false), // EMI
            Spend(Money(99_999), day(2026, 9, 30), Spend.Kind.Energy), // outside the period
        )
        val readings = listOf(
            OdometerReading(41_024.0, day(2026, 10, 2)),
            OdometerReading(41_900.0, day(2026, 10, 8)),
            OdometerReading(42_318.0, day(2026, 10, 20)),
        )
        val metrics = compute(spends, readings, october)

        assertThat(metrics.energySpend).isEqualTo(Money(624_000))
        assertThat(metrics.includedExpenses).isEqualTo(Money(485_000))
        assertThat(metrics.excludedExpenses).isEqualTo(Money(1_200_000))
        assertThat(metrics.totalSpend).isEqualTo(Money(2_309_000))
        assertThat(metrics.distance).isEqualTo(PeriodDistance(1_294.0, isEstimated = false))
        // ₹11,090 / 1,294 km = ₹8.5703… → ₹8.57/km; fuel only ₹6,240 / 1,294 = ₹4.822… → ₹4.82/km
        assertThat(metrics.costPerKm).isEqualTo(Money(857))
        assertThat(metrics.energyCostPerKm).isEqualTo(Money(482))
        // ₹11,090 over 31 days = ₹357.74/day; × 30.4375 days = ₹10,888.77 a month
        assertThat(metrics.costPerDay).isEqualTo(Money(35_774))
        assertThat(metrics.monthlyBurn).isEqualTo(Money(1_088_877))
    }

    @Test
    fun `too few readings inside the period are interpolated and labelled estimated`() {
        val readings = listOf(
            OdometerReading(10_000.0, day(2026, 9, 21)),
            OdometerReading(10_400.0, day(2026, 10, 11)), // the only reading inside October
            OdometerReading(11_000.0, day(2026, 11, 10)),
        )
        val distance = compute.distanceInPeriod(readings, october)!!
        // 1 Oct: 10 days into a 20-day, 400 km gap → 10,200. 1 Nov: 21 of 30 days into a 600 km gap → 10,820.
        assertThat(distance.isEstimated).isTrue()
        assertThat(distance.km).isWithin(0.001).of(620.0)
    }

    @Test
    fun `boundaries beyond known data clamp instead of extrapolating`() {
        val readings = listOf(OdometerReading(9_870.0, day(2026, 10, 10)))
        assertThat(compute.distanceInPeriod(readings, october)).isEqualTo(PeriodDistance(0.0, isEstimated = true))
    }

    @Test
    fun `no readings means no distance and no cost per km`() {
        val metrics = compute(listOf(Spend(Money(10_000), day(2026, 10, 3), Spend.Kind.Energy)), emptyList(), october)
        assertThat(metrics.distance).isNull()
        assertThat(metrics.costPerKm).isNull()
        assertThat(metrics.costBasis).isEqualTo(Money(10_000))
    }

    @Test
    fun `money division rounds half up to the paisa`() {
        assertThat(Money(1_000).divideBy(3.0)).isEqualTo(Money(333))
        assertThat(Money(5).divideBy(2.0)).isEqualTo(Money(3))
        assertThrows<IllegalArgumentException> { Money(1).divideBy(0.0) }
    }

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(IST).toInstant().toEpochMilli()

    private companion object {
        val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}

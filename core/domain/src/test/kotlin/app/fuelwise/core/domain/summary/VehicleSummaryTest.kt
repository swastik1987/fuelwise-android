package app.fuelwise.core.domain.summary

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

class VehicleSummaryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 10, 16).atTime(10, 0).atZone(zone).toInstant()
    private val clock = Clock.fixed(now, zone)
    private val summary =
        ComputeVehicleSummary(ComputeConsumption(), ComputeCostMetrics(), ComputePredictions(clock), clock)
    private val safari = Vehicle(
        id = "safari",
        name = "Safari",
        type = VehicleType.Car,
        tanks = listOf(Tank("diesel", EnergyType.DIESEL, 50.0, isPrimary = true)),
        startOdometerKm = 40_000.0,
        startOdometerAtUtcMs = day(2026, 8, 1),
    )
    private val categories = SystemCategory.entries.mapIndexed { i, c ->
        Category(c.id, c, null, false, c.includeInCostPerKm, i)
    }

    @Test
    fun `no fill-ups yet - no mileage, unlock progress at zero`() {
        val result = summary(safari, emptyList(), emptyList(), categories, zone)
        assertThat(result.hasMileage).isFalse()
        assertThat(result.fullFillCount).isEqualTo(0)
        assertThat(result.lastRefuel).isNull()
    }

    @Test
    fun `month cost per km against last month`() {
        val refuels = listOf(
            fill("s1", day(2026, 9, 1), 40_100.0, paid = 400_000),
            fill("s2", day(2026, 9, 30), 41_100.0, paid = 600_000), // Sept: ₹6,000 over 1,000 km (fill-to-fill)
            fill("o1", day(2026, 10, 2), 41_200.0, paid = 500_000),
            fill("o2", day(2026, 10, 15), 42_200.0, paid = 300_000), // Oct: ₹8,000 over 1,000 km
        )
        val result = summary(safari, refuels, emptyList(), categories, zone)
        assertThat(result.month.costPerKm).isEqualTo(Money(800))
        assertThat(result.previousMonth.costPerKm).isEqualTo(Money(1_000))
        assertThat(result.costPerKmChange!!).isWithin(1e-9).of(-0.2)
    }

    @Test
    fun `loan EMI stays out of cost per km`() {
        val refuels =
            listOf(fill("a", day(2026, 10, 2), 41_000.0), fill("b", day(2026, 10, 15), 41_500.0, paid = 500_000))
        val emi = Expense("e", "safari", SystemCategory.LoanEmi.id, day(2026, 10, 5), "Asia/Kolkata", Money(2_000_000))
        val result = summary(safari, refuels, listOf(emi), categories, zone)
        assertThat(result.month.costPerKm).isEqualTo(Money(1_600)) // ₹8,000 / 500 km, EMI excluded
        assertThat(result.month.excludedExpenses).isEqualTo(Money(2_000_000))
    }

    @Test
    fun `mileage, last fill and range left`() {
        val refuels = listOf(
            fill("a", day(2026, 9, 1), 40_100.0, qty = 50.0),
            fill("b", day(2026, 9, 20), 40_770.0, qty = 50.0), // 13.4 km/L
            fill("c", day(2026, 10, 10), 41_440.0, qty = 50.0), // 13.4 km/L
        )
        val result = summary(safari, refuels, emptyList(), categories, zone)
        assertThat(result.averageInterval!!.kmPerUnit).isWithin(1e-9).of(13.4)
        assertThat(result.lastRefuel!!.id).isEqualTo("c")
        assertThat(result.lastRefuelInterval!!.kmPerUnit).isWithin(1e-9).of(13.4)
        assertThat(result.fullFillCount).isEqualTo(3)
        // 50 L × 13.4 = 670 km, minus ~6 days × 20.6 km/day of predicted driving since the last fill ≈ 546 km.
        assertThat(result.rangeLeftKm!!).isWithin(1.0).of(546.0)
    }

    @Test
    fun `mileage drop needs the last two tanks more than 10 percent below the baseline`() {
        val refuels = listOf(
            fill("a", day(2026, 7, 1), 40_000.0),
            fill("b", day(2026, 7, 15), 40_450.0), // 15 km/L
            fill("c", day(2026, 8, 1), 40_900.0), // 15
            fill("d", day(2026, 8, 15), 41_290.0), // 13
            fill("e", day(2026, 9, 1), 41_680.0), // 13 → last two average 13, baseline 15: −13%
        )
        val drop = summary(safari, refuels, emptyList(), categories, zone).mileageDrop!!
        assertThat(drop.fraction).isWithin(1e-9).of(1 - 13.0 / 15.0)
    }

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun fill(id: String, time: Long, odo: Double, qty: Double = 30.0, paid: Long = 300_000) = Refuel(
        id = id,
        vehicleId = "safari",
        tankId = "diesel",
        dateTimeUtcMs = time,
        zoneId = "Asia/Kolkata",
        odometerKm = odo,
        quantity = qty,
        unitPrice = Money(8_895),
        total = Money(paid),
        isFull = true,
    )
}

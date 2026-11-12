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
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
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

    // Fuel used (PRD F-03.5, 12 Nov 2026): ₹90.00/L at 15 km/L is exactly 600 paise per km.
    private val fills = listOf(
        fill("a", day(2026, 8, 20), 40_100.0, qty = 40.0, price = 9_000, paid = 360_000),
        fill("b", day(2026, 9, 10), 40_700.0, qty = 40.0, price = 9_000, paid = 360_000), // 600 km on 40 L
    )

    // No fill-up in October, but two automatic trips: 120 km and 80 km.
    private val october = listOf(auto("t1", day(2026, 10, 5), 120.0), auto("t2", day(2026, 10, 12), 80.0))

    @Test
    fun `a month without a fill-up costs the fuel its km used`() {
        val result = summary(safari, fills, emptyList(), categories, zone, october)
        assertThat(result.month.distance!!.km).isWithin(1e-6).of(200.0)
        assertThat(result.month.energySpend).isEqualTo(Money(120_000)) // 200 km × ₹6
        assertThat(result.month.totalSpend).isEqualTo(Money(120_000))
        assertThat(result.month.costPerKm).isEqualTo(Money(600))
        assertThat(result.month.paidEnergy).isEqualTo(Money.ZERO)
        assertThat(result.month.energyEstimated).isTrue()
        // September: the km between the fills that fall in it, at the same rate, so no change.
        assertThat(result.previousMonth.costPerKm).isEqualTo(Money(600))
        assertThat(result.costPerKmChange!!).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `month cards run from this month back to the first fill-up`() {
        val months = summary(safari, fills, emptyList(), categories, zone, october).months
        assertThat(months.map { it.month.toString() }).containsExactly("2026-10", "2026-09", "2026-08").inOrder()

        val oct = months[0]
        assertThat(oct.metrics.costPerKm).isEqualTo(Money(600))
        assertThat(oct.trips.count).isEqualTo(2)
        assertThat(oct.trips.distanceKm).isWithin(1e-9).of(200.0)
        // No tank closed in October: September's 15 km/L, carried over.
        assertThat(oct.mileage!!.kmPerUnit).isWithin(1e-9).of(15.0)
        assertThat(oct.mileageCarried).isTrue()
        assertThat(oct.costPerKmChange!!).isWithin(1e-9).of(0.0)

        val sep = months[1]
        assertThat(sep.mileage!!.kmPerUnit).isWithin(1e-9).of(15.0)
        assertThat(sep.mileageCarried).isFalse()
        assertThat(sep.trips.count).isEqualTo(0)

        // August only has the first fill: no tank closed yet, so no mileage.
        assertThat(months[2].mileage).isNull()
    }

    @Test
    fun `a vehicle with nothing logged still gets this month's card`() {
        val months = summary(safari, emptyList(), emptyList(), categories, zone).months
        assertThat(months.map { it.month.toString() }).containsExactly("2026-10")
        assertThat(months[0].metrics.totalSpend).isEqualTo(Money.ZERO)
        assertThat(months[0].mileage).isNull()
    }

    @Test
    fun `loan EMI stays out of cost per km`() {
        val emi = Expense("e", "safari", SystemCategory.LoanEmi.id, day(2026, 10, 5), "Asia/Kolkata", Money(2_000_000))
        val service =
            Expense("s", "safari", SystemCategory.Service.id, day(2026, 10, 6), "Asia/Kolkata", Money(200_000))
        val result = summary(safari, fills, listOf(emi, service), categories, zone, october)
        assertThat(result.month.costPerKm).isEqualTo(Money(1_600)) // (₹1,200 fuel + ₹2,000 service) / 200 km
        assertThat(result.month.excludedExpenses).isEqualTo(Money(2_000_000))
    }

    @Test
    fun `without a rate yet the month is what was paid`() {
        val first = listOf(fill("only", day(2026, 10, 2), 41_000.0, qty = 40.0, price = 9_000, paid = 360_000))
        val result = summary(safari, first, emptyList(), categories, zone)
        assertThat(result.month.energySpend).isEqualTo(Money(360_000))
        assertThat(result.month.energyEstimated).isFalse()
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

    private fun auto(id: String, start: Long, km: Double) = Trip(
        id = id,
        vehicleId = "safari",
        startUtcMs = start,
        zoneId = "Asia/Kolkata",
        distanceKm = km,
        purpose = TripPurpose.Commute,
        endUtcMs = start + 3_600_000,
        source = TripSource.Auto,
    )

    private fun fill(
        id: String,
        time: Long,
        odo: Double,
        qty: Double = 30.0,
        paid: Long = 300_000,
        price: Long = 8_895,
    ) = Refuel(
        id = id,
        vehicleId = "safari",
        tankId = "diesel",
        dateTimeUtcMs = time,
        zoneId = "Asia/Kolkata",
        odometerKm = odo,
        quantity = qty,
        unitPrice = Money(price),
        total = Money(paid),
        isFull = true,
    )
}

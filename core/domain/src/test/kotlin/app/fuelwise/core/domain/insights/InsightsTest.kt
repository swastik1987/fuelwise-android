package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
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
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Test

class InsightsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 10, 16).atTime(10, 0).atZone(zone).toInstant()
    private val clock = Clock.fixed(now, zone)
    private val insights = ComputeInsights(ComputeConsumption(), ComputeCostMetrics(), clock)
    private val categories = SystemCategory.entries.mapIndexed { i, c ->
        Category(c.id, c, null, false, c.includeInCostPerKm, i)
    }

    private val swift = Vehicle(
        id = "swift",
        name = "Swift",
        type = VehicleType.Car,
        tanks = listOf(Tank("petrol", EnergyType.PETROL, 37.0, isPrimary = true)),
        startOdometerKm = 20_000.0,
        startOdometerAtUtcMs = day(2026, 6, 1),
        claimedKmPerUnit = 22.0,
    )

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun fill(
        id: String,
        at: Long,
        odo: Double,
        litres: Double,
        vehicle: String = "swift",
        tank: String = "petrol",
    ) = Refuel(
        id = id,
        vehicleId = vehicle,
        tankId = tank,
        dateTimeUtcMs = at,
        zoneId = zone.id,
        odometerKm = odo,
        quantity = litres,
        unitPrice = Money(10_300),
        total = Money((litres * 10_300).toLong()),
        isFull = true,
    )

    // 4 measured intervals: 18, 18, 20, 15 km/L; the last two average 17.1, below the earlier 18 by under 10%.
    private val refuels = listOf(
        fill("a", day(2026, 7, 1), 20_100.0, 30.0),
        fill("b", day(2026, 7, 20), 20_640.0, 30.0),
        fill("c", day(2026, 8, 10), 21_180.0, 30.0),
        fill("d", day(2026, 9, 5), 21_780.0, 30.0),
        fill("e", day(2026, 10, 5), 22_230.0, 30.0),
    )
    private val service = Expense("s", "swift", SystemCategory.Service.id, day(2026, 10, 8), zone.id, Money(400_000))
    private val emi = Expense("l", "swift", SystemCategory.LoanEmi.id, day(2026, 10, 10), zone.id, Money(1_200_000))
    private val history = VehicleHistory(swift, refuels, listOf(service, emi))

    @Test
    fun `three months covers August to now and compares with the three before`() {
        val resolved = InsightPeriod.ThreeMonths.resolve(now.toEpochMilli(), zone, null)
        assertThat(
            resolved.current.startUtcMs,
        ).isEqualTo(LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().toEpochMilli())
        assertThat(
            resolved.previous!!.startUtcMs,
        ).isEqualTo(LocalDate.of(2026, 5, 1).atStartOfDay(zone).toInstant().toEpochMilli())
        assertThat(resolved.previous.endUtcMs).isEqualTo(resolved.current.startUtcMs)
    }

    @Test
    fun `all time starts at the first entry and has no previous period`() {
        val resolved = InsightPeriod.All.resolve(now.toEpochMilli(), zone, day(2025, 1, 1))
        assertThat(resolved.current.startUtcMs).isEqualTo(day(2025, 1, 1))
        assertThat(resolved.previous).isNull()
    }

    @Test
    fun `period mileage is the sum of distance over the sum of fuel`() {
        val report = insights(listOf(history), categories, InsightPeriod.ThreeMonths, zone)
        // Intervals closed in Aug–Oct: c (540 km), d (600), e (450) over 90 L.
        assertThat(report.mileage.map { it.interval.distanceKm }).containsExactly(540.0, 600.0, 450.0).inOrder()
        assertThat(report.average!!.kmPerUnit).isWithin(1e-9).of(1_590.0 / 90.0)
        assertThat(report.best!!.kmPerUnit).isWithin(1e-9).of(20.0)
        assertThat(report.worst!!.kmPerUnit).isWithin(1e-9).of(15.0)
        // The rolling line for c reaches back to b, outside the period.
        assertThat(report.mileage.first().rollingKmPerUnit).isWithin(1e-9).of(1_080.0 / 60.0)
    }

    @Test
    fun `loan EMI is shown in spend but not in cost per km`() {
        val report = insights(listOf(history), categories, InsightPeriod.Month, zone)
        assertThat(report.metrics.excludedExpenses).isEqualTo(Money(1_200_000))
        assertThat(report.spendByCategory.first().categoryId).isEqualTo(SystemCategory.LoanEmi.id)
        assertThat(report.spendByCategory.map { it.categoryId }).contains(null)
        assertThat(report.monthlySpend.single().month).isEqualTo(YearMonth.of(2026, 10))
    }

    @Test
    fun `real vs claimed and locked cards for a petrol car`() {
        val report = insights(listOf(history), categories, InsightPeriod.All, zone)
        val real = report.cards.filterIsInstance<InsightCard.RealVsClaimed>().single()
        assertThat(real.ratio).isWithin(1e-9).of((2_130.0 / 120.0) / 22.0)
        assertThat(report.cards).contains(InsightCard.ComingSoon(ProInsight.FuelGrade))
        assertThat(report.cards).contains(InsightCard.ComingSoon(ProInsight.Forecast))
        assertThat(report.cards).doesNotContain(InsightCard.ComingSoon(ProInsight.CompareVehicles))
    }

    @Test
    fun `all vehicles adds distances per vehicle and leaves mileage out`() {
        val city = swift.copy(id = "city", name = "City", startOdometerKm = 5_000.0)
        val cityFills = listOf(
            fill("x", day(2026, 10, 2), 5_100.0, 20.0, vehicle = "city"),
            fill("y", day(2026, 10, 14), 5_400.0, 20.0, vehicle = "city"),
        )
        val report =
            insights(
                listOf(history, VehicleHistory(city, cityFills, emptyList())),
                categories,
                InsightPeriod.Month,
                zone,
            )
        assertThat(report.mileage).isEmpty()
        assertThat(report.prices).isEmpty()
        assertThat(report.energyType).isNull()
        assertThat(report.fillCount).isEqualTo(3)
        assertThat(report.cards).contains(InsightCard.ComingSoon(ProInsight.CompareVehicles))
    }

    @Test
    fun `recap picks the best tank closed in the month`() {
        val recap =
            ComputeRecap(ComputeConsumption(), ComputeCostMetrics())(history, categories, YearMonth.of(2026, 9), zone)
        assertThat(recap.bestTank!!.kmPerUnit).isWithin(1e-9).of(20.0)
        // Fuel used (PRD F-03.5): 485.34 km driven in September (interpolated between the fills either side) at
        // ₹103.00 / 17.75 km/L, the vehicle's average, = ₹2,816.32. The September fill paid ₹3,090.
        assertThat(recap.spend).isEqualTo(Money(281_632))
        assertThat(recap.energyType).isEqualTo(EnergyType.PETROL)
    }

    @Test
    fun `milestones - first 1000 km earned, best-ever tank not (last tank was worst)`() {
        val earned = ComputeMilestones(ComputeConsumption(), clock)(history)
        assertThat(earned.map { it.kind }).containsExactly(Milestone.Kind.FirstThousandKm)
        assertThat(earned.single().key).isEqualTo("km1000:swift")
    }

    @Test
    fun `milestones - best-ever tank keyed by the closing fill`() {
        val better = history.copy(refuels = refuels + fill("f", day(2026, 10, 15), 22_900.0, 30.0))
        val earned = ComputeMilestones(ComputeConsumption(), clock)(better)
        val best = earned.single { it.kind == Milestone.Kind.BestEverTank }
        assertThat(best.key).isEqualTo("best:f")
        assertThat(best.kmPerUnit).isWithin(1e-9).of(670.0 / 30.0)
    }

    @Test
    fun `three years of data compute well inside the 300 ms chart budget`() {
        val start = day(2023, 10, 1)
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val fills = (0 until 150).map { i -> fill("w$i", start + i * weekMs, 20_100.0 + i * 420.0, 26.0 + (i % 5)) }
        val costs = (0 until 150).map { i ->
            Expense("x$i", "swift", SystemCategory.Parking.id, start + i * weekMs + 3_600_000, zone.id, Money(5_000))
        }
        val big = VehicleHistory(swift, fills, costs)
        repeat(3) { insights(listOf(big), categories, InsightPeriod.All, zone) }
        val began = System.nanoTime()
        val report = insights(listOf(big), categories, InsightPeriod.All, zone)
        val ms = (System.nanoTime() - began) / 1_000_000
        assertThat(report.mileage).hasSize(149)
        assertThat(ms).isLessThan(300L)
    }
}

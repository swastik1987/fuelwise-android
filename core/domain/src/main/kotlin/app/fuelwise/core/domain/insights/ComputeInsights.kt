package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.ConsumptionReport
import app.fuelwise.core.domain.engine.CostMetrics
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.engine.OdometerReading
import app.fuelwise.core.domain.engine.Period
import app.fuelwise.core.domain.engine.PeriodDistance
import app.fuelwise.core.domain.engine.Spend
import app.fuelwise.core.domain.engine.divideBy
import app.fuelwise.core.domain.engine.fuelUse
import app.fuelwise.core.domain.entry.gpsDistances
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.domain.summary.mileageDrop
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.sum
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** One vehicle's entries, as the Insights screen loads them. */
data class VehicleHistory(
    val vehicle: Vehicle,
    val refuels: List<Refuel>,
    val expenses: List<Expense>,
    // GPS and automatic trips put km into months without a typed reading (fuel used, PRD F-03.5).
    val trips: List<Trip> = emptyList(),
)

/** A measured interval, dated by the full fill that closed it. */
data class MileagePoint(val atUtcMs: Long, val interval: Interval, val rollingKmPerUnit: Double)

data class PricePoint(val atUtcMs: Long, val unitPrice: Money)

data class MonthSpend(val month: YearMonth, val energy: Money, val expenses: Money) {
    val total: Money get() = energy + expenses
}

data class MonthCostPerKm(val month: YearMonth, val costPerKm: Money?)

/** [categoryId] null is fuel / charging. */
data class CategorySpend(val categoryId: String?, val amount: Money)

/** PRD F-06.4 cards. Pro ones show as "Coming soon" in R1 (PRD D15) and unlock with billing, F-19, in R2. */
sealed interface InsightCard {
    data class MileageDrop(val fraction: Double) : InsightCard

    /** [ratio] = your km/L ÷ claimed (0.84 = 84% of claimed). */
    data class RealVsClaimed(val ratio: Double, val claimedKmPerUnit: Double, val actualKmPerUnit: Double) : InsightCard

    data class ComingSoon(val kind: ProInsight) : InsightCard
}

enum class ProInsight { FuelGrade, CngSaving, EvVsPetrol, HomeVsPublic, Forecast, CompareVehicles }

/** Everything the Insights overview and chart details show for one period (PRD F-06.2, F-06.3). */
data class InsightsReport(
    val period: Period,
    val metrics: CostMetrics,
    /** Period ₹/km vs the previous period, as a ratio change. Null for "All" or without both. */
    val costPerKmChange: Double?,
    /** Set when exactly one vehicle is shown: its primary tank's energy, for units and colours. */
    val energyType: EnergyType?,
    val claimedKmPerUnit: Double?,
    val average: Interval?,
    val best: Interval?,
    val worst: Interval?,
    val fillCount: Int,
    val averageFillQuantity: Double?,
    val averageUnitPrice: Money?,
    val mileage: List<MileagePoint>,
    val monthlySpend: List<MonthSpend>,
    val prices: List<PricePoint>,
    val costPerKmByMonth: List<MonthCostPerKm>,
    val odometer: List<OdometerReading>,
    val spendByCategory: List<CategorySpend>,
    val cards: List<InsightCard>,
    /** Total cost of ownership (PRD F-03.5); null when no vehicle has a purchase price. */
    val ownership: OwnershipCost? = null,
) {
    val isEmpty: Boolean get() = metrics.totalSpend == Money.ZERO && fillCount == 0
}

class ComputeInsights @Inject constructor(
    private val consumption: ComputeConsumption,
    private val costMetrics: ComputeCostMetrics,
    private val clock: Clock,
) {
    /**
     * [histories] holds one vehicle for a vehicle view and every active vehicle for "All vehicles"; mileage, prices
     * and the odometer only make sense for one vehicle and stay empty otherwise.
     */
    operator fun invoke(
        histories: List<VehicleHistory>,
        categories: List<Category>,
        selection: InsightPeriod,
        zone: ZoneId,
        vehicleCount: Int = histories.size,
        custom: CustomRange? = null,
        ownershipYears: Int = DEFAULT_OWNERSHIP_YEARS,
    ): InsightsReport {
        val earliest = histories.flatMap { h ->
            h.refuels.map { it.dateTimeUtcMs } + h.expenses.map { it.dateTimeUtcMs }
        }.minOrNull()
        val (period, previous) = selection.resolve(clock.millis(), zone, earliest, custom)
        val included = categories.associate { it.id to it.includeInCostPerKm }
        val prepared = histories.map { Prepared(it, included) }

        val metrics = combinedMetrics(prepared, period)
        val previousMetrics = previous?.let { combinedMetrics(prepared, it) }
        val single = prepared.singleOrNull()
        val vehicle = single?.history?.vehicle
        val primary = vehicle?.primaryTank
        val tankFills = single?.history?.refuels?.filter { it.tankId == primary?.id }.orEmpty()
        val report = consumption(tankFills.map { it.toEngine() })
        val mileage = mileagePoints(report, tankFills, period)
        val periodIntervals = mileage.map { it.interval }
        val average = periodIntervals.reduceOrNull(Interval::plus)
        val periodFills = tankFills.filter { it.dateTimeUtcMs in period }
        val allExpenses = histories.flatMap { it.expenses }.filter { it.dateTimeUtcMs in period }
        val allRefuels = histories.flatMap { it.refuels }.filter { it.dateTimeUtcMs in period }

        return InsightsReport(
            period = period,
            metrics = metrics,
            costPerKmChange = ratio(metrics.costPerKm, previousMetrics?.costPerKm),
            energyType = primary?.energyType,
            claimedKmPerUnit = vehicle?.claimedKmPerUnit,
            average = average,
            best = periodIntervals.maxByOrNull { it.kmPerUnit },
            worst = periodIntervals.minByOrNull { it.kmPerUnit },
            fillCount = allRefuels.size,
            averageFillQuantity = periodFills.takeIf { it.isNotEmpty() }?.map { it.quantity }?.average(),
            averageUnitPrice = periodFills.takeIf { it.isNotEmpty() }?.let { fills ->
                fills.map { it.paid }.sum().divideBy(fills.sumOf { it.quantity })
            },
            mileage = mileage,
            monthlySpend = period.months(zone).map { month ->
                val inMonth = { at: Long -> at in period.clip(month, zone) }
                MonthSpend(
                    month,
                    // Fuel used that month, as on Home (PRD F-03.5); expenses as entered.
                    combinedMetrics(prepared, period.clip(month, zone)).energySpend,
                    allExpenses.filter { inMonth(it.dateTimeUtcMs) }.map { it.amount }.sum(),
                )
            },
            prices = if (single ==
                null
            ) {
                emptyList()
            } else {
                periodFills.map { PricePoint(it.dateTimeUtcMs, it.unitPrice) }
            },
            costPerKmByMonth = period.months(zone).map { month ->
                MonthCostPerKm(month, combinedMetrics(prepared, period.clip(month, zone)).costPerKm)
            },
            odometer = single?.readings?.filter { it.atUtcMs in period }?.sortedBy { it.atUtcMs }.orEmpty(),
            spendByCategory = spendByCategory(metrics.energySpend, allExpenses),
            cards = cards(vehicle, report, average, vehicleCount),
            ownership = ownershipCost(histories, period, metrics.distance?.km, ownershipYears, zone),
        )
    }

    private inner class Prepared(val history: VehicleHistory, included: Map<String, Boolean>) {
        val spends = history.refuels.map { Spend(it.paid, it.dateTimeUtcMs, Spend.Kind.Energy) } +
            history.expenses.map {
                Spend(
                    it.amount,
                    it.dateTimeUtcMs,
                    Spend.Kind.Expense,
                    included[it.categoryId] ?: true,
                )
            }
        val readings = odometerReadings(history.vehicle, history.refuels, history.expenses, history.trips)
        val use = fuelUse(
            history.vehicle,
            history.refuels,
            readings,
            gpsDistances(history.vehicle, history.trips),
            consumption,
        )
    }

    /** Per-vehicle metrics summed, so odometers of different vehicles never mix. */
    private fun combinedMetrics(prepared: List<Prepared>, period: Period): CostMetrics {
        val parts = prepared.map { costMetrics.fuelUsed(it.spends, it.use, period) }
        parts.singleOrNull()?.let { return it }
        val energy = parts.map { it.energySpend }.sum()
        val included = parts.map { it.includedExpenses }.sum()
        val excluded = parts.map { it.excludedExpenses }.sum()
        val distances = parts.mapNotNull { it.distance }
        val distance = distances.takeIf { it.isNotEmpty() }
            ?.let { d -> PeriodDistance(d.sumOf { it.km }, d.any { it.isEstimated }) }
        val km = distance?.km?.takeIf { it > 0.0 }
        val basis = energy + included
        return CostMetrics(
            energySpend = energy,
            includedExpenses = included,
            excludedExpenses = excluded,
            distance = distance,
            costPerKm = km?.let { basis.divideBy(it) },
            energyCostPerKm = km?.let { energy.divideBy(it) },
            costPerDay = basis.divideBy(period.days),
            monthlyBurn = basis.divideBy(period.days / DAYS_PER_MONTH),
            paidEnergy = parts.map { it.paidEnergy }.sum(),
            energyEstimated = parts.any { it.energyEstimated },
        )
    }

    private fun mileagePoints(report: ConsumptionReport, fills: List<Refuel>, period: Period): List<MileagePoint> {
        val byId = fills.associateBy { it.id }
        val measured = report.entries.mapNotNull { entry ->
            val interval = (entry.result as? IntervalResult.Measured)?.interval ?: return@mapNotNull null
            byId[entry.id]?.let { it.dateTimeUtcMs to interval }
        }
        // The rolling line uses earlier intervals too, so the first points in a short period aren't jumpy.
        return measured.mapIndexedNotNull { index, (at, interval) ->
            if (at !in period) return@mapIndexedNotNull null
            val window = measured.subList(maxOf(0, index - ROLLING_WINDOW + 1), index + 1).map { it.second }
            MileagePoint(at, interval, window.reduce(Interval::plus).kmPerUnit)
        }
    }

    // The fuel slice is fuel used in the period, like the overview (PRD F-03.5).
    private fun spendByCategory(energy: Money, expenses: List<Expense>): List<CategorySpend> {
        val byCategory = expenses.groupBy {
            it.categoryId
        }.map { (id, list) -> CategorySpend(id, list.map { it.amount }.sum()) }
        return (listOf(CategorySpend(null, energy)) + byCategory).filter {
            it.amount.paise > 0
        }.sortedByDescending { it.amount.paise }
    }

    private fun cards(
        vehicle: Vehicle?,
        report: ConsumptionReport,
        average: Interval?,
        vehicleCount: Int,
    ): List<InsightCard> = buildList {
        report.mileageDrop()?.let { add(InsightCard.MileageDrop(it.fraction)) }
        val claimed = vehicle?.claimedKmPerUnit
        val actual = average ?: report.average
        // Bi-fuel mileage is an estimate, so it is not held against the claimed figure.
        val comparable = vehicle?.isBiFuel == false && actual != null
        if (comparable && claimed != null && claimed > 0) {
            add(InsightCard.RealVsClaimed(actual.kmPerUnit / claimed, claimed, actual.kmPerUnit))
        }
        if (vehicle != null) {
            when {
                vehicle.isElectric -> {
                    add(InsightCard.ComingSoon(ProInsight.EvVsPetrol))
                    add(InsightCard.ComingSoon(ProInsight.HomeVsPublic))
                }

                vehicle.isBiFuel -> add(InsightCard.ComingSoon(ProInsight.CngSaving))

                else -> add(InsightCard.ComingSoon(ProInsight.FuelGrade))
            }
        }
        add(InsightCard.ComingSoon(ProInsight.Forecast))
        if (vehicleCount > 1) add(InsightCard.ComingSoon(ProInsight.CompareVehicles))
    }

    private fun ratio(current: Money?, previous: Money?): Double? = if (current == null || previous == null ||
        previous.paise == 0L
    ) {
        null
    } else {
        current.paise.toDouble() / previous.paise - 1.0
    }

    private fun Refuel.toEngine() = FillUp(id, odometerKm, dateTimeUtcMs, quantity, isFull, missedPrevious)

    private companion object {
        const val ROLLING_WINDOW = 3
        const val DAYS_PER_MONTH = 30.4375
        const val DEFAULT_OWNERSHIP_YEARS = 5
    }
}

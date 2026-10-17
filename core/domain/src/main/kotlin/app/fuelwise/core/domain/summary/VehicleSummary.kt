package app.fuelwise.core.domain.summary

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.engine.ConsumptionReport
import app.fuelwise.core.domain.engine.CostMetrics
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.engine.Period
import app.fuelwise.core.domain.engine.Spend
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Vehicle
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Everything the Home dashboard shows for one vehicle (design.md screen 5). */
data class VehicleSummary(
    val month: CostMetrics,
    val previousMonth: CostMetrics,
    /** This month's ₹/km vs last month's, as a ratio change (−0.06 = 6% cheaper). Null without both. */
    val costPerKmChange: Double?,
    /** Mileage of the primary tank (exact for single fuel; an estimate on bi-fuel). */
    val latestInterval: Interval?,
    val averageInterval: Interval?,
    /** Up to the last 8 measured intervals, oldest first, for the sparkline. */
    val recentIntervals: List<Interval>,
    /** Full fills on the primary tank, for the "log 2 full tanks" progress (design.md 05b). */
    val fullFillCount: Int,
    val lastRefuel: Refuel?,
    /** The interval [lastRefuel] closed, if it closed one. */
    val lastRefuelInterval: Interval?,
    val rangeLeftKm: Double?,
    val mileageDrop: MileageDrop?,
    val isEstimate: Boolean,
) {
    val hasMileage: Boolean get() = averageInterval != null
}

/** "Your mileage dropped 11% over the last 2 tanks" (PRD F-06.4, free insight). */
data class MileageDrop(val fraction: Double)

class ComputeVehicleSummary @Inject constructor(
    private val consumption: ComputeConsumption,
    private val costMetrics: ComputeCostMetrics,
    private val predictions: ComputePredictions,
    private val clock: Clock,
) {
    operator fun invoke(
        vehicle: Vehicle,
        refuels: List<Refuel>,
        expenses: List<Expense>,
        categories: List<Category>,
        zone: ZoneId,
    ): VehicleSummary {
        val now = clock.millis()
        val thisMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val monthStart = thisMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val previousStart = thisMonth.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val included = categories.associate { it.id to it.includeInCostPerKm }
        val spends = refuels.map { Spend(it.paid, it.dateTimeUtcMs, Spend.Kind.Energy) } +
            expenses.map { Spend(it.amount, it.dateTimeUtcMs, Spend.Kind.Expense, included[it.categoryId] ?: true) }
        val readings = odometerReadings(vehicle, refuels, expenses)
        // The current month runs to now, so cost per day reflects days elapsed, not the whole month.
        val month = costMetrics(spends, readings, Period(monthStart, maxOf(now, monthStart + 1)))
        val previous = costMetrics(spends, readings, Period(previousStart, monthStart))

        val primary = vehicle.primaryTank
        val tankFills = refuels.filter { it.tankId == primary?.id }
        val report = consumption(tankFills.map { it.toEngine() })
        val measured = report.measuredIntervals
        val lastRefuel = refuels.maxByOrNull { it.dateTimeUtcMs }
        val lastReport = if (lastRefuel?.tankId ==
            primary?.id
        ) {
            report
        } else {
            consumption(refuels.filter { it.tankId == lastRefuel?.tankId }.map { it.toEngine() })
        }

        return VehicleSummary(
            month = month,
            previousMonth = previous,
            costPerKmChange = ratio(month.costPerKm?.paise, previous.costPerKm?.paise),
            latestInterval = measured.lastOrNull(),
            averageInterval = report.average,
            recentIntervals = measured.takeLast(SPARKLINE_POINTS),
            fullFillCount = tankFills.count { it.isFull },
            lastRefuel = lastRefuel,
            lastRefuelInterval = lastRefuel?.let {
                (lastReport.resultFor(it.id) as? IntervalResult.Measured)?.interval
            },
            rangeLeftKm = rangeLeft(vehicle, tankFills, report, predictions.predictedOdometer(readings)),
            mileageDrop = report.mileageDrop(),
            isEstimate = vehicle.isBiFuel,
        )
    }

    private fun rangeLeft(
        vehicle: Vehicle,
        tankFills: List<Refuel>,
        report: ConsumptionReport,
        odometer: Double?,
    ): Double? {
        val last = tankFills.maxWithOrNull(compareBy({ it.odometerKm }, { it.dateTimeUtcMs })) ?: return null
        vehicle.custom.rangeKm?.let { range ->
            if (!last.isFull || odometer == null) return null
            return (range - (odometer - last.odometerKm).coerceAtLeast(0.0)).coerceAtLeast(0.0)
        }
        val kmPerUnit = vehicle.custom.kmPerUnit ?: report.average?.kmPerUnit
        return predictions.rangeLeftKm(vehicle.primaryTank?.capacity, kmPerUnit, last.toEngine(), odometer)
    }

    private fun ratio(current: Long?, previous: Long?): Double? =
        if (current == null || previous == null || previous == 0L) null else current.toDouble() / previous - 1.0

    private fun Refuel.toEngine() = FillUp(id, odometerKm, dateTimeUtcMs, quantity, isFull, missedPrevious)

    private companion object {
        const val SPARKLINE_POINTS = 8
    }
}

/** The last two intervals together below the up-to-five before them by more than 10% (PRD F-06.4). */
fun ConsumptionReport.mileageDrop(): MileageDrop? {
    val intervals = measuredIntervals
    if (intervals.size < MIN_INTERVALS_FOR_DROP) return null
    val recent = intervals.takeLast(RECENT_FOR_DROP).reduce(Interval::plus)
    val baseline = intervals.dropLast(RECENT_FOR_DROP).takeLast(BASELINE_FOR_DROP).reduce(Interval::plus)
    val change = recent.kmPerUnit / baseline.kmPerUnit - 1.0
    return if (change < -DROP_THRESHOLD) MileageDrop(-change) else null
}

private const val RECENT_FOR_DROP = 2
private const val BASELINE_FOR_DROP = 5
private const val MIN_INTERVALS_FOR_DROP = 3
private const val DROP_THRESHOLD = 0.10

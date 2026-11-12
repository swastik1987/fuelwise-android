package app.fuelwise.core.domain.summary

import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.ConsumptionReport
import app.fuelwise.core.domain.engine.CostMetrics
import app.fuelwise.core.domain.engine.FuelUse
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.engine.Period
import app.fuelwise.core.domain.engine.Spend
import app.fuelwise.core.domain.trip.MonthTrips
import app.fuelwise.core.domain.trip.monthTrips
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One month card on Home (user feedback, 12 Nov 2026): cost, km, trips and mileage for [month]. */
data class MonthSummary(
    val month: YearMonth,
    /** Fuel used plus expenses for the month (PRD F-03.5); the current month runs to now. */
    val metrics: CostMetrics,
    /** ₹/km vs the month before, as a ratio change (−0.06 = 6% cheaper). Null without both. */
    val costPerKmChange: Double?,
    /** Tanks closed in the month, combined; or, with [mileageCarried], the last tank closed before it ended. */
    val mileage: Interval?,
    val mileageCarried: Boolean,
    val trips: MonthTrips,
)

/** What [monthSummaries] reads, already worked out once by [ComputeVehicleSummary]. */
internal class MonthInputs(
    val vehicle: Vehicle,
    val refuels: List<Refuel>,
    val expenses: List<Expense>,
    val trips: List<Trip>,
    val spends: List<Spend>,
    val use: FuelUse,
    /** The primary tank's consumption report and its fills. */
    val report: ConsumptionReport,
    val tankFills: List<Refuel>,
)

/**
 * This month (always present, up to [now]) and every earlier month back to the first fill-up, expense or trip,
 * newest first, at most [MAX_MONTHS]. A past month with no spend, no km and no trips is left out.
 */
internal fun monthSummaries(
    inputs: MonthInputs,
    costMetrics: ComputeCostMetrics,
    now: Long,
    zone: ZoneId,
): List<MonthSummary> {
    val thisMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
    val trips = inputs.trips.filter { it.vehicleId == inputs.vehicle.id && it.status == TripStatus.Done }
    val first = firstMonth(inputs, trips, thisMonth, zone)
    val tanks = closedTanks(inputs)

    val periods = generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= thisMonth }.associateWith { month ->
        val start = month.startMs(zone)
        val end = if (month == thisMonth) maxOf(now, start + 1) else month.plusMonths(1).startMs(zone)
        Period(start, end)
    }
    val costs = periods.mapValues { (_, period) -> costMetrics.fuelUsed(inputs.spends, inputs.use, period) }

    return periods.keys.sortedDescending().mapNotNull { month ->
        val period = periods.getValue(month)
        val cost = costs.getValue(month)
        val monthTrips = monthTrips(trips, inputs.vehicle.id, month, zone)
        val empty = cost.totalSpend.paise == 0L && (cost.distance?.km ?: 0.0) <= 0.0 && monthTrips.count == 0
        if (empty && month != thisMonth) return@mapNotNull null
        val closed = tanks.filter { it.closedUtcMs >= period.startUtcMs && it.closedUtcMs < period.endUtcMs }
        val carried = if (closed.isEmpty()) tanks.lastOrNull { it.closedUtcMs < period.endUtcMs } else null
        MonthSummary(
            month = month,
            metrics = cost,
            costPerKmChange = change(cost.costPerKm?.paise, costs[month.minusMonths(1)]?.costPerKm?.paise),
            mileage = closed.map { it.interval }.reduceOrNull(Interval::plus) ?: carried?.interval,
            mileageCarried = carried != null,
            trips = monthTrips,
        )
    }
}

private class ClosedTank(val closedUtcMs: Long, val interval: Interval)

/** The month of the first fill-up, expense or trip, at most [MAX_MONTHS] back. */
private fun firstMonth(inputs: MonthInputs, trips: List<Trip>, thisMonth: YearMonth, zone: ZoneId): YearMonth {
    val earliest = (
        inputs.refuels.map { it.dateTimeUtcMs } + inputs.expenses.map { it.dateTimeUtcMs } + trips.map { it.startUtcMs }
        ).minOrNull() ?: return thisMonth
    return YearMonth.from(Instant.ofEpochMilli(earliest).atZone(zone))
        .coerceIn(thisMonth.minusMonths(MAX_MONTHS - 1L), thisMonth)
}

/** Measured tanks on the primary tank, oldest first; each counts in the month its closing fill was made. */
private fun closedTanks(inputs: MonthInputs): List<ClosedTank> {
    val closedAt = inputs.tankFills.associate { it.id to it.dateTimeUtcMs }
    return inputs.report.entries.mapNotNull { entry ->
        val interval = (entry.result as? IntervalResult.Measured)?.interval ?: return@mapNotNull null
        closedAt[entry.id]?.let { ClosedTank(it, interval) }
    }.sortedBy { it.closedUtcMs }
}

private fun YearMonth.startMs(zone: ZoneId) = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

private fun change(current: Long?, previous: Long?): Double? =
    if (current == null || previous == null || previous == 0L) null else current.toDouble() / previous - 1.0

/** Ten years of month cards at most. */
internal const val MAX_MONTHS = 120

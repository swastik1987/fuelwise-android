package app.fuelwise.core.domain.entry

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.LogbookKind
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Vehicle
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToLong

/** Tile 1 of a fuel card when the fill closed no measured interval (owner choice 23 Nov 2026: a grey label). */
enum class NoMileage { Partial, Baseline, Restarted }

/** How a fill's km/L compares with the tank's average: ±10% around it is usual (Logbook card tint). */
enum class MileageBand {
    Better,
    Usual,
    Worse,
    ;

    companion object {
        const val BAND = 0.10

        fun of(vsAverage: Double?): MileageBand = when {
            vsAverage == null -> Usual
            vsAverage >= BAND -> Better
            vsAverage <= -BAND -> Worse
            else -> Usual
        }
    }
}

/** What a Logbook card shows beyond the entry itself (design "Logbook entry card", 23 Nov 2026). */
sealed interface EntryFacts {
    /** A fill-up or charge. [vsAverage] is a fraction: -0.34 is 34% below the tank's average km per unit. */
    data class Fill(
        val unit: QuantityUnit?,
        val interval: Interval?,
        val vsAverage: Double?,
        val noMileage: NoMileage?,
        val kmSinceLast: Double?,
        val costPerKm: Money?,
    ) : EntryFacts {
        val kmPerUnit: Double? get() = interval?.kmPerUnit
        val band: MileageBand get() = MileageBand.of(vsAverage)
    }

    /**
     * An expense or toll, against the vehicle's previous entry in the same category. [hasEarlier] false means this is
     * the first one; [kmSinceSame] needs both odometers.
     */
    data class Spend(val hasEarlier: Boolean, val kmSinceSame: Double?, val daysSinceSame: Long?) : EntryFacts
}

/**
 * Per-entry figures for the Logbook card, from the whole history (the list itself is paged). Fills are compared within
 * their tank, in the consumption engine's order (PRD F-03.1); expenses within their vehicle and category.
 */
class LogbookFacts @Inject constructor(private val consumption: ComputeConsumption) {
    operator fun invoke(
        refuels: List<Refuel>,
        expenses: List<Expense>,
        vehicles: List<Vehicle>,
        zone: ZoneId,
    ): Map<String, EntryFacts> {
        val units = vehicles.flatMap { it.tanks }.associate { it.id to it.energyType.canonicalUnit }
        val facts = HashMap<String, EntryFacts>(refuels.size + expenses.size)
        refuels.groupBy { it.tankId }.forEach { (tankId, fills) -> fillFacts(fills, units[tankId], facts) }
        expenses.groupBy { it.vehicleId to it.categoryId }.values.forEach { spendFacts(it, zone, facts) }
        return facts
    }

    private fun fillFacts(fills: List<Refuel>, unit: QuantityUnit?, into: MutableMap<String, EntryFacts>) {
        val report = consumption(
            fills.map { FillUp(it.id, it.odometerKm, it.dateTimeUtcMs, it.quantity, it.isFull, it.missedPrevious) },
        )
        val average = report.average?.kmPerUnit
        val byId = fills.associateBy { it.id }
        var previous: Refuel? = null
        report.entries.forEach { entry ->
            val fill = byId.getValue(entry.id)
            val km = previous?.let { fill.odometerKm - it.odometerKm }?.takeIf { it > 0 }
            val interval = (entry.result as? IntervalResult.Measured)?.interval
            into[fill.id] = EntryFacts.Fill(
                unit = unit,
                interval = interval,
                vsAverage = interval?.let { i -> average?.takeIf { it > 0 }?.let { i.kmPerUnit / it - 1 } },
                noMileage = when (entry.result) {
                    is IntervalResult.Measured -> null
                    IntervalResult.Partial -> NoMileage.Partial
                    IntervalResult.Baseline -> NoMileage.Baseline
                    is IntervalResult.Invalid -> NoMileage.Restarted
                },
                kmSinceLast = km,
                costPerKm = km?.let { Money((fill.paid.paise / it).roundToLong()) },
            )
            previous = fill
        }
    }

    private fun spendFacts(sameCategory: List<Expense>, zone: ZoneId, into: MutableMap<String, EntryFacts>) {
        var previous: Expense? = null
        sameCategory.sortedWith(compareBy({ it.dateTimeUtcMs }, { it.id })).forEach { expense ->
            val before = previous
            into[expense.id] = EntryFacts.Spend(
                hasEarlier = before != null,
                kmSinceSame = before?.odometerKm?.let { b -> expense.odometerKm?.minus(b) }?.takeIf { it > 0 },
                daysSinceSame = before?.let {
                    ChronoUnit.DAYS.between(it.dateTimeUtcMs.localDate(zone), expense.dateTimeUtcMs.localDate(zone))
                },
            )
            previous = expense
        }
    }
}

/** The right-hand side of a Logbook month header, which depends on the type filter (design, 23 Nov 2026). */
sealed interface MonthSummary {
    /** All entries, or several types: "2 fills · 1 expense". Zero counts are left out. */
    data class Counts(val fills: Int, val charges: Int, val expenses: Int, val tolls: Int) : MonthSummary

    /**
     * Fuel or Charge only: "62.4 L · 509 km · 8.2 km/L". [quantity] and [kmPerUnit] are null when the month mixes
     * units (kg and L, say, across vehicles); [kmPerUnit] is from the measured intervals closed that month.
     */
    data class Energy(val quantity: Double?, val unit: QuantityUnit?, val km: Double, val kmPerUnit: Double?) :
        MonthSummary

    /** Expenses or Tolls only: the category IDs that month, most recent first. */
    data class Categories(val categoryIds: List<String>) : MonthSummary

    companion object {
        /** Summaries per local month for [entries] already filtered like the list, under the type filter [kinds]. */
        fun of(
            entries: List<LogbookEntry>,
            kinds: Set<LogbookKind>,
            facts: Map<String, EntryFacts>,
            zone: ZoneId,
        ): Map<YearMonth, MonthSummary> = entries
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.dateTimeUtcMs).atZone(zone)) }
            .mapValues { (_, month) ->
                when (kinds.singleOrNull()) {
                    LogbookKind.Fuel, LogbookKind.Charge -> energy(month, facts)

                    LogbookKind.Expense, LogbookKind.Toll -> Categories(
                        month.sortedByDescending { it.dateTimeUtcMs }.mapNotNull { it.categoryId }.distinct(),
                    )

                    null -> Counts(
                        fills = month.count { it.kind == LogbookKind.Fuel },
                        charges = month.count { it.kind == LogbookKind.Charge },
                        expenses = month.count { it.kind == LogbookKind.Expense },
                        tolls = month.count { it.kind == LogbookKind.Toll },
                    )
                }
            }

        private fun energy(month: List<LogbookEntry>, facts: Map<String, EntryFacts>): Energy {
            val fills = month.mapNotNull { facts[it.id] as? EntryFacts.Fill }
            val unit = fills.mapNotNull { it.unit }.distinct().singleOrNull()
            val measured = fills.mapNotNull { it.interval }.reduceOrNull(Interval::plus)
            return Energy(
                quantity = unit?.let { month.sumOf { it.quantity ?: 0.0 } },
                unit = unit,
                km = fills.sumOf { it.kmSinceLast ?: 0.0 },
                kmPerUnit = if (unit != null) measured?.kmPerUnit else null,
            )
        }
    }
}

private fun Long.localDate(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

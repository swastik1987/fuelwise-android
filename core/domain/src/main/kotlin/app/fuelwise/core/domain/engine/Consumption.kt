package app.fuelwise.core.domain.engine

import javax.inject.Inject

/**
 * One fill-up (or charge, for the full-charge EV method) as the engine sees it. Quantities are in the tank's
 * canonical unit (L, kg or kWh) and distances in km (CLAUDE.md rule 2).
 */
data class FillUp(
    val id: String,
    val odometerKm: Double,
    val dateTimeUtcMs: Long,
    val quantity: Double,
    val isFull: Boolean,
    val missedPrevious: Boolean = false,
)

/** What the engine says about a single fill-up. Shown on that fill-up's row (PRD F-03.1 step 7). */
sealed interface IntervalResult {
    /** A full fill that opens the first interval, or reopens one after a reset. No consumption shown. */
    data object Baseline : IntervalResult

    /** A partial fill. Never shows its own consumption; its quantity counts toward the next full fill. */
    data object Partial : IntervalResult

    /** A full fill that closes an interval that can't be measured honestly. The baseline resets here. */
    data class Invalid(val reason: Reason) : IntervalResult {
        enum class Reason {
            /** A fill in the interval was marked "missed logging the previous fill-up". */
            MissedPrevious,

            /** The odometer did not increase across the interval (data entry error). */
            NoDistance,

            /** EV SoC method: start/end SoC or usable battery capacity is missing. */
            MissingSoc,

            /** EV SoC method: the battery shows no energy used (likely an unlogged charge in between). */
            InconsistentSoc,
        }
    }

    /** A measured interval closed by this full fill. */
    data class Measured(val interval: Interval) : IntervalResult
}

/** Distance driven and quantity used between two full fills. */
data class Interval(val distanceKm: Double, val quantity: Double) {
    /** Canonical consumption: L, kg or kWh per 100 km (PRD F-03.2). */
    val per100Km: Double get() = quantity / distanceKm * HUNDRED_KM

    /** km per L / kg / kWh. */
    val kmPerUnit: Double get() = distanceKm / quantity

    operator fun plus(other: Interval) = Interval(distanceKm + other.distanceKm, quantity + other.quantity)
}

/**
 * Per-fill results plus the weighted average. [entries] follow the engine's ordering (odometer, then time).
 * [average] is Σ quantity / Σ distance over measured intervals — never a mean of interval means (F-03.1 step 8).
 */
data class ConsumptionReport(val entries: List<Entry>, val average: Interval?) {
    data class Entry(val id: String, val result: IntervalResult)

    val measuredIntervals: List<Interval>
        get() = entries.mapNotNull { (it.result as? IntervalResult.Measured)?.interval }

    fun resultFor(id: String): IntervalResult? = entries.firstOrNull { it.id == id }?.result

    companion object {
        val EMPTY = ConsumptionReport(emptyList(), null)
    }
}

/**
 * Full-tank consumption for one tank (PRD F-03.1). Pure and deterministic: input order does not matter.
 * Editing any historical entry is handled by calling this again with the full list.
 */
class ComputeConsumption @Inject constructor() {
    operator fun invoke(fills: List<FillUp>): ConsumptionReport {
        if (fills.isEmpty()) return ConsumptionReport.EMPTY
        val sorted = fills.sortedWith(ENGINE_ORDER)
        val entries = ArrayList<ConsumptionReport.Entry>(sorted.size)
        var baseline: FillUp? = null
        var quantitySinceBaseline = 0.0
        var missedSinceBaseline = false
        var total: Interval? = null

        for (fill in sorted) {
            val open = baseline
            val result: IntervalResult = when {
                open == null && fill.isFull -> IntervalResult.Baseline

                open == null -> IntervalResult.Partial

                else -> {
                    quantitySinceBaseline += fill.quantity
                    missedSinceBaseline = missedSinceBaseline || fill.missedPrevious
                    when {
                        !fill.isFull -> IntervalResult.Partial

                        missedSinceBaseline -> IntervalResult.Invalid(IntervalResult.Invalid.Reason.MissedPrevious)

                        fill.odometerKm - open.odometerKm <= 0.0 ->
                            IntervalResult.Invalid(IntervalResult.Invalid.Reason.NoDistance)

                        else -> {
                            val interval = Interval(fill.odometerKm - open.odometerKm, quantitySinceBaseline)
                            total = total?.plus(interval) ?: interval
                            IntervalResult.Measured(interval)
                        }
                    }
                }
            }
            if (fill.isFull) {
                // Every full fill closes the current interval (measured or not) and opens the next one.
                baseline = fill
                quantitySinceBaseline = 0.0
                missedSinceBaseline = false
            }
            entries += ConsumptionReport.Entry(fill.id, result)
        }
        return ConsumptionReport(entries, total)
    }

    companion object {
        /** Odometer first, date-time as the tiebreaker, id last so equal entries still sort stably. */
        val ENGINE_ORDER: Comparator<FillUp> =
            compareBy<FillUp>({ it.odometerKm }, { it.dateTimeUtcMs }, { it.id })
    }
}

internal const val HUNDRED_KM = 100.0

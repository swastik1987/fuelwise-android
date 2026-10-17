package app.fuelwise.core.domain.entry

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.engine.divideBy
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.sum
import javax.inject.Inject
import kotlin.math.abs

/** What the post-save sheet shows (PRD F-02.7, design.md screen 7). */
sealed interface FillReward {
    /** First full fill (or first after a reset): "Baseline set. One more full tank…". */
    data object Baseline : FillReward

    /** A partial fill: no interval closes, nothing to celebrate yet. */
    data object Partial : FillReward

    /** The interval couldn't be measured (missed fill, odometer error). Saved, but no number to show. */
    data object NotMeasured : FillReward

    data class Measured(
        val interval: Interval,
        /** Change vs the weighted average of the other intervals; null when this is the first measured interval. */
        val changeVsAverage: Double?,
        /** Money paid for the fills in this interval ÷ its distance. */
        val costPerKm: Money,
        /** Deviates more than 25% from the last five intervals (PRD F-02.6). Shown as a soft chip; never blocks. */
        val unusual: Boolean,
    ) : FillReward
}

class ComputeFillReward @Inject constructor(private val consumption: ComputeConsumption) {
    /** [tankFills] are all fill-ups of the saved fill's tank, including the saved one ([savedId]). */
    operator fun invoke(tankFills: List<Refuel>, savedId: String): FillReward {
        val asEngine = tankFills.map { it.toEngine() }
        val report = consumption(asEngine)
        val ordered = asEngine.sortedWith(ComputeConsumption.ENGINE_ORDER)
        val index = ordered.indexOfFirst { it.id == savedId }
        return when (val result = report.resultFor(savedId)) {
            null, IntervalResult.Partial -> FillReward.Partial

            IntervalResult.Baseline -> FillReward.Baseline

            is IntervalResult.Invalid -> FillReward.NotMeasured

            is IntervalResult.Measured -> {
                val interval = result.interval
                // The interval's fills are the ones after the previous full fill, up to and including this one.
                val start = ordered.subList(0, index).indexOfLast { it.isFull } + 1
                val paidById = tankFills.associate { it.id to it.paid }
                val paid = ordered.subList(start, index + 1).mapNotNull { paidById[it.id] }.sum()

                val earlier = report.entries.take(report.entries.indexOfFirst { it.id == savedId })
                    .mapNotNull { (it.result as? IntervalResult.Measured)?.interval }
                val others = report.measuredIntervals.filterNot { it === interval }
                val average = others.reduceOrNull { a, b -> a + b }
                val recent = earlier.takeLast(ROLLING_WINDOW).reduceOrNull { a, b -> a + b }

                FillReward.Measured(
                    interval = interval,
                    changeVsAverage = average?.let { interval.kmPerUnit / it.kmPerUnit - 1.0 },
                    costPerKm = paid.divideBy(interval.distanceKm),
                    unusual = recent != null && abs(interval.kmPerUnit / recent.kmPerUnit - 1.0) > UNUSUAL_THRESHOLD,
                )
            }
        }
    }

    private fun Refuel.toEngine() = FillUp(id, odometerKm, dateTimeUtcMs, quantity, isFull, missedPrevious)

    private companion object {
        const val ROLLING_WINDOW = 5
        const val UNUSUAL_THRESHOLD = 0.25
    }
}

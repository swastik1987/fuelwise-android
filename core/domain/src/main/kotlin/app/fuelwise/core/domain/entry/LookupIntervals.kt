package app.fuelwise.core.domain.entry

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.model.Refuel
import javax.inject.Inject

/**
 * The engine's verdict for every fill-up, keyed by id, computed per tank. The Logbook uses it for the efficiency
 * chip on closing fills (PRD F-05) and the entry detail for its "this interval" facts.
 */
class LookupIntervals @Inject constructor(private val consumption: ComputeConsumption) {
    operator fun invoke(refuels: List<Refuel>): Map<String, IntervalResult> = refuels
        .groupBy { it.tankId }
        .values
        .flatMap { tankFills ->
            consumption(
                tankFills.map {
                    FillUp(it.id, it.odometerKm, it.dateTimeUtcMs, it.quantity, it.isFull, it.missedPrevious)
                },
            )
                .entries
                .map { it.id to it.result }
        }
        .toMap()

    /** km per unit for fills that closed a measured interval. */
    fun kmPerUnit(refuels: List<Refuel>): Map<String, Double> = invoke(refuels).mapNotNull { (id, r) ->
        (r as? IntervalResult.Measured)?.let { id to it.interval.kmPerUnit }
    }.toMap()
}

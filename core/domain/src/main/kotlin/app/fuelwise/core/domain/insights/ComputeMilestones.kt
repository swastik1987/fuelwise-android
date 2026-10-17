package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.entry.odometerReadings
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** Earned, not streaks (PRD F-17). [key] is stable so a milestone is shown once. */
data class Milestone(val kind: Kind, val vehicleId: String, val key: String, val kmPerUnit: Double? = null) {
    enum class Kind { FirstThousandKm, TenThousandKm, OneYear, BestEverTank }
}

class ComputeMilestones @Inject constructor(private val consumption: ComputeConsumption, private val clock: Clock) {
    /** Every milestone [history] has earned; the caller filters out the ones already shown. */
    operator fun invoke(history: VehicleHistory): List<Milestone> {
        val vehicle = history.vehicle
        val id = vehicle.id
        val entryReadings = odometerReadings(vehicle, history.refuels, history.expenses).drop(1)
        val logged = if (entryReadings.isEmpty()) {
            0.0
        } else {
            entryReadings.maxOf { it.odometerKm } -
                minOf(vehicle.startOdometerKm, entryReadings.minOf { it.odometerKm })
        }
        val first = (history.refuels.map { it.dateTimeUtcMs } + history.expenses.map { it.dateTimeUtcMs }).minOrNull()
        return buildList {
            if (logged >= FIRST_KM) add(Milestone(Milestone.Kind.FirstThousandKm, id, "km1000:$id"))
            if (logged >= TEN_THOUSAND_KM) add(Milestone(Milestone.Kind.TenThousandKm, id, "km10000:$id"))
            if (first != null && Duration.ofMillis(clock.millis() - first).toDays() >= DAYS_PER_YEAR) {
                add(Milestone(Milestone.Kind.OneYear, id, "year1:$id"))
            }
            bestEverTank(history)?.let { add(it) }
        }
    }

    /** The latest interval beats every earlier one, with at least [MIN_INTERVALS_FOR_BEST] to compare. */
    private fun bestEverTank(history: VehicleHistory): Milestone? {
        val primary = history.vehicle.primaryTank ?: return null
        val fills = history.refuels.filter { it.tankId == primary.id }
        val measured = consumption(
            fills.map {
                FillUp(it.id, it.odometerKm, it.dateTimeUtcMs, it.quantity, it.isFull, it.missedPrevious)
            },
        )
            .entries
            .mapNotNull { e -> (e.result as? IntervalResult.Measured)?.let { e.id to it.interval.kmPerUnit } }
        if (measured.size < MIN_INTERVALS_FOR_BEST) return null
        val (lastId, last) = measured.last()
        return if (measured.dropLast(1).all { it.second < last }) {
            Milestone(Milestone.Kind.BestEverTank, history.vehicle.id, "best:$lastId", last)
        } else {
            null
        }
    }

    private companion object {
        const val FIRST_KM = 1_000.0
        const val TEN_THOUSAND_KM = 10_000.0
        const val DAYS_PER_YEAR = 365
        const val MIN_INTERVALS_FOR_BEST = 3
    }
}

package app.fuelwise.core.domain.entry

import app.fuelwise.core.model.Refuel
import javax.inject.Inject

/**
 * Validation and guards for a new or edited fill-up (PRD F-02.6). Problems block saving; warnings don't.
 */
class CheckFillUp @Inject constructor() {
    sealed interface Problem {
        /** Odometer is not above the previous entry by date. [previous] is shown with a "View entry" link. */
        data class OdometerNotAbovePrevious(val previous: Refuel) : Problem

        /** Odometer is not below a later entry (a back-dated fill with too high a reading). */
        data class OdometerNotBelowNext(val next: Refuel) : Problem
    }

    sealed interface Warning {
        /** More than the tank holds (with 3% slack for pump and tank tolerance). */
        data class MoreThanTank(val capacity: Double) : Warning
    }

    data class Result(val problems: List<Problem>, val warnings: List<Warning>)

    /**
     * [existing] are the tank's other fill-ups (the one being edited excluded). Ordering is by date-time, which is
     * what the user sees; the engine then re-sorts by odometer.
     */
    operator fun invoke(
        dateTimeUtcMs: Long,
        odometerKm: Double?,
        quantity: Double?,
        tankCapacity: Double?,
        existing: List<Refuel>,
    ): Result {
        val problems = buildList {
            if (odometerKm != null) {
                existing.filter { it.dateTimeUtcMs <= dateTimeUtcMs }.maxByOrNull { it.dateTimeUtcMs }
                    ?.takeIf { odometerKm <= it.odometerKm }
                    ?.let { add(Problem.OdometerNotAbovePrevious(it)) }
                existing.filter { it.dateTimeUtcMs > dateTimeUtcMs }.minByOrNull { it.dateTimeUtcMs }
                    ?.takeIf { odometerKm >= it.odometerKm }
                    ?.let { add(Problem.OdometerNotBelowNext(it)) }
            }
        }
        val warnings = buildList {
            if (quantity != null && tankCapacity != null && quantity > tankCapacity * SHORT_FILL_SLACK) {
                add(Warning.MoreThanTank(tankCapacity))
            }
        }
        return Result(problems, warnings)
    }

    private companion object {
        const val SHORT_FILL_SLACK = 1.03
    }
}

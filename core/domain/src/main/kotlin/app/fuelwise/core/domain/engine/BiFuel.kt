package app.fuelwise.core.domain.engine

import javax.inject.Inject

/** A fill-up tagged with the tank it went into. */
data class TankFill(val tankId: String, val fill: FillUp)

/**
 * Consumption for every tank of one vehicle (PRD F-03.3).
 *
 * On a bi-fuel vehicle each tank's figure is an estimate: the distance in an interval was partly driven on the other
 * fuel, so per-fuel km/kg or km/L can't be exact. The UI labels these "est." and leads with combined cost/km instead
 * (see [ComputeCostMetrics]), which is exact.
 */
data class VehicleConsumption(val byTank: Map<String, ConsumptionReport>, val isEstimate: Boolean)

class ComputeVehicleConsumption @Inject constructor(private val consumption: ComputeConsumption) {
    /** [isBiFuel] comes from the vehicle's energy setup, not from which tanks happen to have fills yet. */
    operator fun invoke(fills: List<TankFill>, isBiFuel: Boolean): VehicleConsumption {
        val byTank = fills
            .groupBy(keySelector = { it.tankId }, valueTransform = { it.fill })
            .mapValues { (_, tankFills) -> consumption(tankFills) }
        return VehicleConsumption(byTank, isEstimate = isBiFuel)
    }
}

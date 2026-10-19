package app.fuelwise.core.domain.trip

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.sum
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** How a trip's fuel cost was estimated, so the UI can show its working (PRD §1.3: honest numbers). */
sealed interface TripCostBasis {
    /** Single fuel: distance ÷ average km per unit × the last price paid (PRD F-07.1). */
    data class Efficiency(
        val energyType: EnergyType,
        val quantity: Double,
        val kmPerUnit: Double,
        val unitPrice: Money,
        /** The km per unit or the price came from the vehicle's custom parameters (PRD F-01.4). */
        val isManual: Boolean,
    ) : TripCostBasis

    /**
     * Bi-fuel, EV, or no measured tank yet: what fuel or charging has cost per km across the vehicle's history.
     * For bi-fuel this is the exact combined figure (PRD F-03.3); per-fuel efficiency would only be an estimate.
     */
    data class CostPerKm(val costPerKm: Money) : TripCostBasis
}

data class TripCostEstimate(val fuelCost: Money, val basis: TripCostBasis)

/** Estimated fuel or charging cost of a trip, from the vehicle's own numbers. Null without enough history. */
class EstimateTripCost @Inject constructor(private val consumption: ComputeConsumption) {
    operator fun invoke(vehicle: Vehicle, refuels: List<Refuel>, distanceKm: Double): TripCostEstimate? {
        if (distanceKm <= 0.0) return null
        val own = refuels.filter { it.vehicleId == vehicle.id }
        efficiencyEstimate(vehicle, own, distanceKm)?.let { return it }
        val perKm = historyCostPerKm(own) ?: return null
        return TripCostEstimate(perKm.times(distanceKm), TripCostBasis.CostPerKm(perKm))
    }

    private fun efficiencyEstimate(vehicle: Vehicle, own: List<Refuel>, distanceKm: Double): TripCostEstimate? {
        val tank = vehicle.primaryTank ?: return null
        if (vehicle.isBiFuel || tank.energyType == EnergyType.ELECTRIC) return null
        val fills = own.filter { it.tankId == tank.id }
        val measured = consumption(fills.map { it.toEngine() }).average?.kmPerUnit
        val kmPerUnit = vehicle.custom.kmPerUnit ?: measured
        val price = vehicle.custom.pricePerUnit ?: fills.maxByOrNull { it.dateTimeUtcMs }?.unitPrice
        if (kmPerUnit == null || kmPerUnit <= 0.0 || price == null) return null
        val quantity = distanceKm / kmPerUnit
        return TripCostEstimate(
            price.times(quantity),
            TripCostBasis.Efficiency(
                energyType = tank.energyType,
                quantity = quantity,
                kmPerUnit = kmPerUnit,
                unitPrice = price,
                isManual = vehicle.custom.kmPerUnit != null || vehicle.custom.pricePerUnit != null,
            ),
        )
    }

    /**
     * Paid across the odometer span of all fill-ups and charges ÷ that span. The first entry is left out: its fuel
     * was burnt before the span began.
     */
    private fun historyCostPerKm(own: List<Refuel>): Money? {
        val sorted = own.sortedWith(compareBy({ it.odometerKm }, { it.dateTimeUtcMs }))
        if (sorted.size < 2) return null
        val span = sorted.last().odometerKm - sorted.first().odometerKm
        if (span <= 0.0) return null
        val paid = sorted.drop(1).map { it.paid }.sum()
        val perKm = BigDecimal.valueOf(paid.paise).divide(BigDecimal.valueOf(span), 0, RoundingMode.HALF_UP)
        return Money(perKm.longValueExact())
    }

    private fun Refuel.toEngine() = FillUp(id, odometerKm, dateTimeUtcMs, quantity, isFull, missedPrevious)
}

/** Money × a quantity, rounded half-up to the paisa. */
internal fun Money.times(factor: Double): Money {
    val paise = BigDecimal.valueOf(paise).multiply(BigDecimal.valueOf(factor)).setScale(0, RoundingMode.HALF_UP)
    return Money(paise.longValueExact())
}

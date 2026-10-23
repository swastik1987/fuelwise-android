package app.fuelwise.core.domain.entry

import app.fuelwise.core.model.Refuel

/**
 * "Trip meter since last fill" (PRD F-02.4): the reading is the odometer at the vehicle's previous fill-up plus the
 * trip meter. The previous fill is the latest one before [atUtcMs] on any tank, since a trip meter is usually reset at
 * every stop at the pump. Null when there's no earlier fill to add to.
 */
fun previousFillOdometer(fills: List<Refuel>, atUtcMs: Long, excludeId: String? = null): Double? =
    fills.filter { it.dateTimeUtcMs < atUtcMs && it.id != excludeId }.maxByOrNull { it.dateTimeUtcMs }?.odometerKm

fun odometerFromTripMeter(previousFillOdometerKm: Double, tripKm: Double): Double = previousFillOdometerKm + tripKm

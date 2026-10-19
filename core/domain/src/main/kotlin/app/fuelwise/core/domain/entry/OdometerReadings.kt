package app.fuelwise.core.domain.entry

import app.fuelwise.core.domain.engine.OdometerReading
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle

/**
 * Every odometer reading known for a vehicle: the one typed when it was added, plus entries that carry one, and
 * finished trips' start and end readings (PRD F-07.6 feeds predictions). Trips of other vehicles are ignored.
 */
fun odometerReadings(
    vehicle: Vehicle,
    refuels: List<Refuel>,
    expenses: List<Expense>,
    trips: List<Trip> = emptyList(),
): List<OdometerReading> = buildList {
    add(OdometerReading(vehicle.startOdometerKm, vehicle.startOdometerAtUtcMs))
    refuels.forEach { add(OdometerReading(it.odometerKm, it.dateTimeUtcMs)) }
    expenses.forEach { e -> e.odometerKm?.let { add(OdometerReading(it, e.dateTimeUtcMs)) } }
    trips.filter { it.vehicleId == vehicle.id && it.status == TripStatus.Done }.forEach { t ->
        t.startOdometerKm?.let { add(OdometerReading(it, t.startUtcMs)) }
        t.endOdometerKm?.let { add(OdometerReading(it, t.endUtcMs ?: t.startUtcMs)) }
    }
}

/**
 * The hint for a GPS trip's end odometer (PRD F-07.6): its start reading, or the last reading before it began, plus
 * the distance recorded. A hint only; the user always types the value.
 */
fun tripEndOdometerHint(trip: Trip, readings: List<OdometerReading>): Double? {
    val start = trip.startOdometerKm
        ?: readings.filter { it.atUtcMs <= trip.startUtcMs }.maxByOrNull { it.atUtcMs }?.odometerKm
        ?: return null
    return start + trip.distanceKm
}

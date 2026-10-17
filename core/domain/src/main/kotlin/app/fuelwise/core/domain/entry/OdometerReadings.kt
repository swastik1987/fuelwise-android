package app.fuelwise.core.domain.entry

import app.fuelwise.core.domain.engine.OdometerReading
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Vehicle

/** Every odometer reading known for a vehicle: the one typed when it was added, plus entries that carry one. */
fun odometerReadings(vehicle: Vehicle, refuels: List<Refuel>, expenses: List<Expense>): List<OdometerReading> =
    buildList {
        add(OdometerReading(vehicle.startOdometerKm, vehicle.startOdometerAtUtcMs))
        refuels.forEach { add(OdometerReading(it.odometerKm, it.dateTimeUtcMs)) }
        expenses.forEach { e -> e.odometerKm?.let { add(OdometerReading(it, e.dateTimeUtcMs)) } }
    }

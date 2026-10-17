package app.fuelwise.core.testing

import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType

/** The fictional vehicles from design.md §11, for tests and screenshots. */
object SampleVehicles {
    val safari = Vehicle(
        id = "safari",
        name = "Safari",
        type = VehicleType.Car,
        tanks = listOf(Tank("safari-diesel", EnergyType.DIESEL, 50.0, isPrimary = true)),
        startOdometerKm = 42_318.0,
        startOdometerAtUtcMs = 0,
        registrationNumber = "KA 01 AB 1234",
        emissionNorm = EmissionNorm.BS6,
        claimedKmPerUnit = 19.1,
        sortOrder = 0,
    )

    val ertiga = Vehicle(
        id = "ertiga",
        name = "Ertiga",
        type = VehicleType.Car,
        tanks = listOf(
            Tank("ertiga-cng", EnergyType.CNG, 12.0, isPrimary = true),
            Tank("ertiga-petrol", EnergyType.PETROL, 45.0, isPrimary = false),
        ),
        startOdometerKm = 31_045.0,
        startOdometerAtUtcMs = 0,
        registrationNumber = "MH 02 CD 5678",
        sortOrder = 1,
    )

    val nexonEv = Vehicle(
        id = "nexon",
        name = "Nexon EV",
        type = VehicleType.Car,
        tanks = listOf(Tank("nexon-battery", EnergyType.ELECTRIC, 40.5, isPrimary = true)),
        startOdometerKm = 18_402.0,
        startOdometerAtUtcMs = 0,
        registrationNumber = "DL 3C EF 9012",
        sortOrder = 2,
    )

    val activa = Vehicle(
        id = "activa",
        name = "Activa",
        type = VehicleType.Scooter,
        tanks = listOf(Tank("activa-petrol", EnergyType.PETROL, 5.3, isPrimary = true)),
        startOdometerKm = 9_870.0,
        startOdometerAtUtcMs = 0,
        registrationNumber = "KA 05 GH 3456",
        sortOrder = 3,
        isArchived = true,
    )

    val all = listOf(safari, ertiga, nexonEv, activa)
}

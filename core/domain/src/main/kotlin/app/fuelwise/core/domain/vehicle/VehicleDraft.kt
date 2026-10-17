package app.fuelwise.core.domain.vehicle

import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.VehicleType
import javax.inject.Inject

/**
 * What the Add vehicle form collects (design.md screen 2). Only name, type, fuel setup and odometer are required
 * (PRD F-01 AC). [tankCapacities] follows the order of [FuelSetup.tanks]; null means "not entered".
 */
data class VehicleDraft(
    val name: String = "",
    val type: VehicleType = VehicleType.Car,
    val fuelSetup: FuelSetup = FuelSetup.Petrol,
    val odometerKm: Double? = null,
    val tankCapacities: List<Double?> = emptyList(),
    val registrationNumber: String = "",
    val emissionNorm: EmissionNorm? = null,
    val claimedKmPerUnit: Double? = null,
)

enum class VehicleDraftError {
    NameRequired,
    NameTooLong,
    OdometerRequired,
    OdometerNegative,
    CapacityNotPositive,
    RegistrationInvalid,
    ClaimedNotPositive,
}

class ValidateVehicleDraft @Inject constructor() {
    operator fun invoke(draft: VehicleDraft): Set<VehicleDraftError> = buildSet {
        val name = draft.name.trim()
        when {
            name.isEmpty() -> add(VehicleDraftError.NameRequired)
            name.length > MAX_NAME_LENGTH -> add(VehicleDraftError.NameTooLong)
        }
        when {
            draft.odometerKm == null -> add(VehicleDraftError.OdometerRequired)
            draft.odometerKm < 0.0 -> add(VehicleDraftError.OdometerNegative)
        }
        if (draft.tankCapacities.any { it != null && it <= 0.0 }) add(VehicleDraftError.CapacityNotPositive)
        if (draft.registrationNumber.isNotBlank() && !RegistrationNumber.isValid(draft.registrationNumber)) {
            add(VehicleDraftError.RegistrationInvalid)
        }
        if (draft.claimedKmPerUnit != null && draft.claimedKmPerUnit <= 0.0) add(VehicleDraftError.ClaimedNotPositive)
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}

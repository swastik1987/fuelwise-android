package app.fuelwise.core.domain.vehicle

import app.fuelwise.core.model.CustomParameters
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.VehicleType
import java.time.Clock
import java.time.LocalDate
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
    // PRD F-01.1: free text in R1.
    val make: String = "",
    val model: String = "",
    val variant: String = "",
    val year: Int? = null,
    val purchaseDate: LocalDate? = null,
    val purchaseOdometerKm: Double? = null,
    val purchasePrice: Money? = null,
    /** PRD F-01.2: a petrol hybrid (HEV) is tracked as petrol, with a tag. */
    val isHybrid: Boolean = false,
    /** PRD F-01.2: the grade a petrol tank's first fill-up starts on. */
    val defaultGrade: FuelGrade? = null,
    /** PRD F-01.4: overrides for estimates; null uses the vehicle's own history. */
    val custom: CustomParameters = CustomParameters(),
)

enum class VehicleDraftError {
    NameRequired,
    NameTooLong,
    OdometerRequired,
    OdometerNegative,
    CapacityNotPositive,
    RegistrationInvalid,
    ClaimedNotPositive,
    YearInvalid,
    PurchaseInFuture,
    PurchaseOdometerInvalid,
    PurchasePriceNotPositive,
    CustomNotPositive,
}

class ValidateVehicleDraft @Inject constructor(private val clock: Clock) {
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
        addAll(purchaseErrors(draft))
        val custom = draft.custom
        val overrides = listOfNotNull(custom.kmPerUnit, custom.rangeKm, custom.pricePerUnit?.paise?.toDouble())
        if (overrides.any { it <= 0.0 }) add(VehicleDraftError.CustomNotPositive)
    }

    /** Model years run up to next year (new models go on sale early); a purchase can't be in the future. */
    private fun purchaseErrors(draft: VehicleDraft): Set<VehicleDraftError> = buildSet {
        val today = LocalDate.now(clock)
        if (draft.year != null && draft.year !in MIN_YEAR..today.year + 1) add(VehicleDraftError.YearInvalid)
        if (draft.purchaseDate?.isAfter(today) == true) add(VehicleDraftError.PurchaseInFuture)
        // Bought at a reading above today's doesn't add up (odometers only go forward).
        val maxBought = draft.odometerKm ?: Double.MAX_VALUE
        if (draft.purchaseOdometerKm?.let { it !in 0.0..maxBought } == true) {
            add(VehicleDraftError.PurchaseOdometerInvalid)
        }
        if ((draft.purchasePrice?.paise ?: 1) <= 0) add(VehicleDraftError.PurchasePriceNotPositive)
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
        const val MIN_YEAR = 1950
    }
}

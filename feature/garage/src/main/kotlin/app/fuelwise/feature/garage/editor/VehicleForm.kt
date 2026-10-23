package app.fuelwise.feature.garage.editor

import app.fuelwise.core.domain.vehicle.RegistrationNumber
import app.fuelwise.core.domain.vehicle.VehicleDraft
import app.fuelwise.core.domain.vehicle.VehicleDraftError
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.format.NumberInput
import java.time.LocalDate

/**
 * Form state for design.md screen 2. Numbers are kept as text while typing and parsed on save.
 * In edit mode the fuel setup and starting odometer are fixed: changing them would rewrite history.
 */
data class VehicleFormState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val onboarding: Boolean = false,
    val name: String = "",
    val type: VehicleType = VehicleType.Car,
    val fuelSetup: FuelSetup = FuelSetup.Petrol,
    val odometer: String = "",
    val capacities: List<String> = listOf(""),
    val registration: String = "",
    val emissionNorm: EmissionNorm? = null,
    val claimed: String = "",
    /** Make, model, variant, year and purchase details (PRD F-01.1), keyed by field. */
    val details: Map<VehicleDetail, String> = emptyMap(),
    val purchaseDate: LocalDate? = null,
    /** PRD F-01.2: petrol only. */
    val isHybrid: Boolean = false,
    val defaultGrade: FuelGrade? = null,
    val moreExpanded: Boolean = false,
    /** Shown only after the first save attempt, so an empty form doesn't open covered in red. */
    val errors: Set<VehicleDraftError> = emptySet(),
    val isSaving: Boolean = false,
    /**
     * Car Bluetooth for automatic trips (design.md screen 22, PRD F-21.3): offered when editing a saved vehicle
     * while automatic trips are available. [carLinkName] is the linked car, or null.
     */
    val carBluetoothOffered: Boolean = false,
    val carLinked: Boolean = false,
    val carLinkName: String? = null,
) {
    /** Two-wheelers have no car kit to link (design.md: "Two-wheelers can skip this"). */
    val showCarBluetooth: Boolean
        get() = carBluetoothOffered && type != VehicleType.Scooter && type != VehicleType.Motorcycle

    /** Hybrid tag and default grade apply to a petrol tank (PRD F-01.2). */
    val hasPetrolTank: Boolean get() = EnergyType.PETROL in fuelSetup.tanks

    fun detail(field: VehicleDetail): String = details[field].orEmpty()
}

/** The optional text fields under "More details". */
enum class VehicleDetail(val maxLength: Int) {
    Make(MAX_TEXT),
    Model(MAX_TEXT),
    Variant(MAX_TEXT),
    Year(YEAR_DIGITS),
    PurchaseOdometer(MAX_NUMBER),
    PurchasePrice(MAX_NUMBER),
}

private const val MAX_TEXT = 30
private const val YEAR_DIGITS = 4

// Up to 99,99,999.9 km or ₹99,99,99,999.99 as typed, with separators.
private const val MAX_NUMBER = 14

/** Cleans what was typed: digits for the year, a number for the reading and the price. */
internal fun VehicleDetail.sanitize(value: String): String = when (this) {
    VehicleDetail.Year -> value.filter(Char::isDigit)
    VehicleDetail.PurchaseOdometer -> NumberInput.sanitize(value, 1)
    VehicleDetail.PurchasePrice -> NumberInput.sanitize(value, 2)
    else -> value
}.take(maxLength)

internal fun VehicleFormState.toDraft() = VehicleDraft(
    name = name,
    type = type,
    fuelSetup = fuelSetup,
    odometerKm = NumberInput.parseDecimal(odometer),
    tankCapacities = capacities.map(NumberInput::parseDecimal),
    registrationNumber = registration,
    emissionNorm = emissionNorm,
    claimedKmPerUnit = NumberInput.parseDecimal(claimed),
    make = detail(VehicleDetail.Make),
    model = detail(VehicleDetail.Model),
    variant = detail(VehicleDetail.Variant),
    year = detail(VehicleDetail.Year).toIntOrNull(),
    purchaseDate = purchaseDate,
    purchaseOdometerKm = NumberInput.parseDecimal(detail(VehicleDetail.PurchaseOdometer)),
    purchasePrice = detail(VehicleDetail.PurchasePrice).takeIf { it.isNotBlank() }?.let(MoneyInput::parse),
    isHybrid = isHybrid && hasPetrolTank && !fuelSetup.isBiFuel,
    defaultGrade = defaultGrade.takeIf { hasPetrolTank },
)

/** The saved vehicle's details as form text. */
internal fun Vehicle.detailsText(): Map<VehicleDetail, String> = buildMap {
    make?.let { put(VehicleDetail.Make, it) }
    model?.let { put(VehicleDetail.Model, it) }
    variant?.let { put(VehicleDetail.Variant, it) }
    year?.let { put(VehicleDetail.Year, it.toString()) }
    purchaseOdometerKm?.let { put(VehicleDetail.PurchaseOdometer, NumberInput.plain(it)) }
    purchasePrice?.let { put(VehicleDetail.PurchasePrice, MoneyInput.plain(it)) }
}

internal fun Vehicle.applyDraft(draft: VehicleDraft) = copy(
    name = draft.name.trim(),
    type = draft.type,
    registrationNumber = draft.registrationNumber.takeIf { it.isNotBlank() }?.let(RegistrationNumber::format),
    emissionNorm = draft.emissionNorm,
    claimedKmPerUnit = draft.claimedKmPerUnit,
    make = draft.make.trim().ifEmpty { null },
    model = draft.model.trim().ifEmpty { null },
    variant = draft.variant.trim().ifEmpty { null },
    year = draft.year,
    purchaseDate = draft.purchaseDate,
    purchaseOdometerKm = draft.purchaseOdometerKm,
    purchasePrice = draft.purchasePrice,
    isHybrid = draft.isHybrid,
    tanks = tanks.mapIndexed { i, tank ->
        tank.copy(
            capacity = draft.tankCapacities.getOrNull(i),
            defaultGrade = draft.defaultGrade.takeIf { tank.energyType == EnergyType.PETROL },
        )
    },
)

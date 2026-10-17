package app.fuelwise.core.model

import java.time.LocalDate

/** PRD F-01.1. */
enum class VehicleType {
    Car,
    Scooter,
    Motorcycle,
    AutoRickshaw,
    Van,
    Truck,
    Other,
    ;

    /** Two-wheelers get shorter service intervals and a tighter "due soon" window (PRD F-11). */
    val isTwoWheeler: Boolean get() = this == Scooter || this == Motorcycle
}

/** Drives PUC defaults; the rules themselves live in assets/rules/india.json (CLAUDE.md rule 8). */
enum class EmissionNorm { BS3, BS4, BS6 }

/**
 * The energy setup picked when adding a vehicle (design.md screen 2). Bi-fuel setups create two tanks;
 * the first listed is primary (PRD F-01.2).
 */
enum class FuelSetup(val tanks: List<EnergyType>) {
    Petrol(listOf(EnergyType.PETROL)),
    Diesel(listOf(EnergyType.DIESEL)),
    CngPetrol(listOf(EnergyType.CNG, EnergyType.PETROL)),
    Electric(listOf(EnergyType.ELECTRIC)),
    LpgPetrol(listOf(EnergyType.LPG, EnergyType.PETROL)),
    EthanolFlex(listOf(EnergyType.ETHANOL_FLEX)),
    ;

    val isBiFuel: Boolean get() = tanks.size > 1
}

/** One tank or battery. [capacity] is in the energy type's canonical unit (L, kg or usable kWh). */
data class Tank(val id: String, val energyType: EnergyType, val capacity: Double?, val isPrimary: Boolean)

/**
 * A vehicle (PRD F-01). Only name, type, energy setup and the current odometer are required.
 * [startOdometerKm] is the reading entered when the vehicle was added; it anchors predictions until fill-ups exist.
 */
data class Vehicle(
    val id: String,
    val name: String,
    val type: VehicleType,
    val tanks: List<Tank>,
    val startOdometerKm: Double,
    val startOdometerAtUtcMs: Long,
    val make: String? = null,
    val model: String? = null,
    val variant: String? = null,
    val year: Int? = null,
    val registrationNumber: String? = null,
    val emissionNorm: EmissionNorm? = null,
    val purchaseDate: LocalDate? = null,
    val purchaseOdometerKm: Double? = null,
    val purchasePrice: Money? = null,
    /** ARAI / claimed figure in km per unit of the primary tank (PRD F-01.3). */
    val claimedKmPerUnit: Double? = null,
    val isHybrid: Boolean = false,
    val custom: CustomParameters = CustomParameters(),
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
) {
    val primaryTank: Tank? get() = tanks.firstOrNull { it.isPrimary } ?: tanks.firstOrNull()
    val isBiFuel: Boolean get() = tanks.size > 1
    val isElectric: Boolean get() = tanks.all { it.energyType == EnergyType.ELECTRIC }
}

/** User overrides for estimates, shown as "manual" in the UI (PRD F-01.4). Null = computed. */
data class CustomParameters(
    val kmPerUnit: Double? = null,
    val pricePerUnit: Money? = null,
    val rangeKm: Double? = null,
)

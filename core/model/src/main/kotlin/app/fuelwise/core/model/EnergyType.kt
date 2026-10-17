package app.fuelwise.core.model

/** Fuel or energy carried by a tank (PRD F-01.2). Canonical storage unit per type is fixed. */
enum class EnergyType(val canonicalUnit: QuantityUnit) {
    PETROL(QuantityUnit.LITRE),
    DIESEL(QuantityUnit.LITRE),
    CNG(QuantityUnit.KILOGRAM),
    LPG(QuantityUnit.LITRE),
    ELECTRIC(QuantityUnit.KILOWATT_HOUR),
    ETHANOL_FLEX(QuantityUnit.LITRE),
}

/** Canonical storage units (CLAUDE.md rule 2). Conversions to display units live in `core:domain` units. */
enum class QuantityUnit {
    LITRE,
    KILOGRAM,
    KILOWATT_HOUR,
}

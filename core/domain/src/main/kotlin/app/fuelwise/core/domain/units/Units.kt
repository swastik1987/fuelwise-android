package app.fuelwise.core.domain.units

/**
 * Display-unit conversions. Storage is always km, L, kg and kWh (CLAUDE.md rule 2);
 * convert only at the UI edge with these functions.
 */
enum class DistanceUnit(internal val kmPerUnit: Double) {
    KILOMETRE(1.0),
    MILE(KM_PER_MILE),
    ;

    fun fromKm(km: Double): Double = km / kmPerUnit

    fun toKm(value: Double): Double = value * kmPerUnit
}

enum class VolumeUnit(internal val litresPerUnit: Double) {
    LITRE(1.0),
    US_GALLON(LITRES_PER_US_GALLON),
    UK_GALLON(LITRES_PER_UK_GALLON),
    ;

    fun fromLitres(litres: Double): Double = litres / litresPerUnit

    fun toLitres(value: Double): Double = value * litresPerUnit
}

/**
 * How efficiency is shown. The engine computes quantity per 100 km internally (PRD F-03.2);
 * these convert that canonical figure for display. India default is distance per quantity (km/L, km/kg, km/kWh).
 */
enum class EfficiencyUnit {
    /** km/L, km/kg or km/kWh. */
    DISTANCE_PER_QUANTITY,

    /** L/100 km, kg/100 km or kWh/100 km. */
    QUANTITY_PER_100_DISTANCE,

    /** Miles per US gallon (liquid fuels only). */
    MPG_US,

    /** Miles per UK gallon (liquid fuels only). */
    MPG_UK,
    ;

    /** Converts a canonical quantity-per-100-km figure into this unit. Returns null for a non-positive input. */
    fun fromPer100Km(per100Km: Double): Double? {
        if (per100Km <= 0.0) return null
        return when (this) {
            QUANTITY_PER_100_DISTANCE -> per100Km
            DISTANCE_PER_QUANTITY -> HUNDRED / per100Km
            MPG_US -> (HUNDRED / per100Km) * LITRES_PER_US_GALLON / KM_PER_MILE
            MPG_UK -> (HUNDRED / per100Km) * LITRES_PER_UK_GALLON / KM_PER_MILE
        }
    }
}

const val KM_PER_MILE = 1.609344
const val LITRES_PER_US_GALLON = 3.785411784
const val LITRES_PER_UK_GALLON = 4.54609
private const val HUNDRED = 100.0

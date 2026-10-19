package app.fuelwise.core.domain.units

import app.fuelwise.core.domain.units.EfficiencyLabel.KG_PER_100KM
import app.fuelwise.core.domain.units.EfficiencyLabel.KM_PER_KG
import app.fuelwise.core.domain.units.EfficiencyLabel.KM_PER_KWH
import app.fuelwise.core.domain.units.EfficiencyLabel.KM_PER_L
import app.fuelwise.core.domain.units.EfficiencyLabel.KWH_PER_100KM
import app.fuelwise.core.domain.units.EfficiencyLabel.L_PER_100KM
import app.fuelwise.core.domain.units.EfficiencyLabel.MI_PER_KG
import app.fuelwise.core.domain.units.EfficiencyLabel.MI_PER_KWH
import app.fuelwise.core.domain.units.EfficiencyLabel.MPG_UK
import app.fuelwise.core.domain.units.EfficiencyLabel.MPG_US
import app.fuelwise.core.model.QuantityUnit

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

/** Every efficiency label the UI can show; each maps to one string resource. */
enum class EfficiencyLabel {
    KM_PER_L,
    KM_PER_KG,
    KM_PER_KWH,
    L_PER_100KM,
    KG_PER_100KM,
    KWH_PER_100KM,
    MPG_US,
    MPG_UK,
    MI_PER_KG,
    MI_PER_KWH,
}

/** An efficiency figure ready for display: the converted [value] and the [label] it is shown with. */
data class EfficiencyFigure(val value: Double, val label: EfficiencyLabel)

/**
 * The units a vehicle's figures are shown in (PRD F-18). Defaults are India's: km, L and km/L (km/kg, km/kWh).
 * Settable globally and per vehicle; storage never changes.
 */
data class DisplayUnits(
    val distance: DistanceUnit = DistanceUnit.KILOMETRE,
    val volume: VolumeUnit = VolumeUnit.LITRE,
    val efficiency: EfficiencyUnit = EfficiencyUnit.DISTANCE_PER_QUANTITY,
) {
    /**
     * Converts a canonical km-per-unit figure (km/L, km/kg or km/kWh, as the engine reports it) for [quantity].
     * Miles-per-gallon only exists for liquids, so CNG and EV fall back to mi/kg and mi/kWh. Returns null for a
     * non-positive input.
     */
    fun efficiency(kmPerUnit: Double, quantity: QuantityUnit): EfficiencyFigure? {
        if (kmPerUnit <= 0.0) return null
        val milesPerUnit = kmPerUnit / KM_PER_MILE
        return when (efficiency) {
            EfficiencyUnit.DISTANCE_PER_QUANTITY ->
                EfficiencyFigure(kmPerUnit, quantity.pick(KM_PER_L, KM_PER_KG, KM_PER_KWH))

            EfficiencyUnit.QUANTITY_PER_100_DISTANCE ->
                EfficiencyFigure(HUNDRED / kmPerUnit, quantity.pick(L_PER_100KM, KG_PER_100KM, KWH_PER_100KM))

            EfficiencyUnit.MPG_US -> EfficiencyFigure(
                if (quantity == QuantityUnit.LITRE) milesPerUnit * LITRES_PER_US_GALLON else milesPerUnit,
                quantity.pick(MPG_US, MI_PER_KG, MI_PER_KWH),
            )

            EfficiencyUnit.MPG_UK -> EfficiencyFigure(
                if (quantity == QuantityUnit.LITRE) milesPerUnit * LITRES_PER_UK_GALLON else milesPerUnit,
                quantity.pick(MPG_UK, MI_PER_KG, MI_PER_KWH),
            )
        }
    }

    /** Whether a higher number is better in this unit; L/100 km reads the other way round. */
    val higherIsBetter: Boolean get() = efficiency != EfficiencyUnit.QUANTITY_PER_100_DISTANCE

    /** A canonical quantity (L, kg or kWh) in the chosen volume unit; only litres convert. */
    fun quantity(value: Double, unit: QuantityUnit): Double =
        if (unit == QuantityUnit.LITRE) volume.fromLitres(value) else value

    private fun QuantityUnit.pick(litre: EfficiencyLabel, kg: EfficiencyLabel, kwh: EfficiencyLabel) = when (this) {
        QuantityUnit.LITRE -> litre
        QuantityUnit.KILOGRAM -> kg
        QuantityUnit.KILOWATT_HOUR -> kwh
    }

    companion object {
        val INDIA = DisplayUnits()
    }
}

/** The app-wide [default] plus per-vehicle overrides (PRD F-18 "settable per vehicle"). */
data class UnitSettings(
    val default: DisplayUnits = DisplayUnits.INDIA,
    val perVehicle: Map<String, DisplayUnits> = emptyMap(),
) {
    fun forVehicle(vehicleId: String?): DisplayUnits = vehicleId?.let { perVehicle[it] } ?: default
}

const val KM_PER_MILE = 1.609344
const val LITRES_PER_US_GALLON = 3.785411784
const val LITRES_PER_UK_GALLON = 4.54609
private const val HUNDRED = 100.0

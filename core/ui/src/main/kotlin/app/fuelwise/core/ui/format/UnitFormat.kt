package app.fuelwise.core.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.domain.units.DistanceUnit
import app.fuelwise.core.domain.units.EfficiencyLabel
import app.fuelwise.core.domain.units.VolumeUnit
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.R

/**
 * Figures in the user's display units (PRD F-18). Values arrive canonical (km, L, kg, kWh, km per unit) and are
 * converted here, at the UI edge, with core:domain units (CLAUDE.md rule 2).
 */
object UnitFormat {
    @StringRes
    fun label(label: EfficiencyLabel): Int = when (label) {
        EfficiencyLabel.KM_PER_L -> R.string.unit_km_per_l
        EfficiencyLabel.KM_PER_KG -> R.string.unit_km_per_kg
        EfficiencyLabel.KM_PER_KWH -> R.string.unit_km_per_kwh
        EfficiencyLabel.L_PER_100KM -> R.string.unit_l_per_100km
        EfficiencyLabel.KG_PER_100KM -> R.string.unit_kg_per_100km
        EfficiencyLabel.KWH_PER_100KM -> R.string.unit_kwh_per_100km
        EfficiencyLabel.MPG_US -> R.string.unit_mpg_us
        EfficiencyLabel.MPG_UK -> R.string.unit_mpg_uk
        EfficiencyLabel.MI_PER_KG -> R.string.unit_mi_per_kg
        EfficiencyLabel.MI_PER_KWH -> R.string.unit_mi_per_kwh
    }

    @StringRes
    fun distance(unit: DistanceUnit): Int = when (unit) {
        DistanceUnit.KILOMETRE -> R.string.unit_km
        DistanceUnit.MILE -> R.string.unit_mile
    }

    @StringRes
    fun volume(unit: VolumeUnit): Int = when (unit) {
        VolumeUnit.LITRE -> R.string.unit_litre
        VolumeUnit.US_GALLON -> R.string.unit_us_gallon
        VolumeUnit.UK_GALLON -> R.string.unit_uk_gallon
    }

    /** The unit a canonical quantity is shown in: the chosen volume unit for litres, kg and kWh otherwise. */
    @StringRes
    fun quantity(unit: QuantityUnit, units: DisplayUnits): Int = when (unit) {
        QuantityUnit.LITRE -> volume(units.volume)
        QuantityUnit.KILOGRAM -> R.string.unit_kg
        QuantityUnit.KILOWATT_HOUR -> R.string.unit_kwh
    }
}

/** The number alone, e.g. "16.8" (or "5.9" in L/100 km); "—" when there's nothing to show. */
fun efficiencyValue(kmPerUnit: Double?, quantity: QuantityUnit, units: DisplayUnits, decimals: Int = 1): String =
    kmPerUnit?.let { units.efficiency(it, quantity) }?.let { IndianNumberFormat.decimal(it.value, decimals) } ?: "—"

/** The unit alone, e.g. "km/L" or "L/100 km". */
@Composable
fun efficiencyUnit(quantity: QuantityUnit, units: DisplayUnits): String =
    stringResource(UnitFormat.label(units.efficiency(1.0, quantity)?.label ?: EfficiencyLabel.KM_PER_L))

/** "16.8 km/L", "5.9 L/100 km" or "39.5 mpg (US)". */
@Composable
fun efficiencyText(kmPerUnit: Double, quantity: QuantityUnit, units: DisplayUnits, decimals: Int = 1): String {
    val figure = units.efficiency(kmPerUnit, quantity) ?: return "—"
    return "${IndianNumberFormat.decimal(figure.value, decimals)} ${stringResource(UnitFormat.label(figure.label))}"
}

/** "42,318 km" or "26,295 mi". */
@Composable
fun distanceText(km: Double, units: DisplayUnits, decimals: Int = 0): String {
    val label = stringResource(UnitFormat.distance(units.distance))
    return "${IndianNumberFormat.decimal(units.distance.fromKm(km), decimals)} $label"
}

/** "32.4 L", "8.6 US gal" or "6.2 kg". */
@Composable
fun quantityText(value: Double, unit: QuantityUnit, units: DisplayUnits, decimals: Int = 1): String {
    val label = stringResource(UnitFormat.quantity(unit, units))
    return "${IndianNumberFormat.decimal(units.quantity(value, unit), decimals)} $label"
}

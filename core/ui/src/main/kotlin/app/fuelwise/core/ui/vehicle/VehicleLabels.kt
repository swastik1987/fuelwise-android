package app.fuelwise.core.ui.vehicle

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.EnergyColors
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.VehicleType
import app.fuelwise.core.ui.R
import app.fuelwise.core.ui.format.IndianNumberFormat

/** Shared names, icons and colours for vehicle concepts, so every feature says "CNG + Petrol" the same way. */
object VehicleLabels {
    @StringRes
    fun type(type: VehicleType): Int = when (type) {
        VehicleType.Car -> R.string.vehicle_type_car
        VehicleType.Scooter -> R.string.vehicle_type_scooter
        VehicleType.Motorcycle -> R.string.vehicle_type_motorcycle
        VehicleType.AutoRickshaw -> R.string.vehicle_type_auto
        VehicleType.Van -> R.string.vehicle_type_van
        VehicleType.Truck -> R.string.vehicle_type_truck
        VehicleType.Other -> R.string.vehicle_type_other
    }

    @DrawableRes
    fun typeIcon(type: VehicleType): Int = when (type) {
        VehicleType.Car, VehicleType.Other -> FwIcons.Car
        VehicleType.Scooter -> FwIcons.Scooter
        VehicleType.Motorcycle -> FwIcons.Motorcycle
        VehicleType.AutoRickshaw -> FwIcons.AutoRickshaw
        VehicleType.Van, VehicleType.Truck -> FwIcons.Truck
    }

    @StringRes
    fun energy(type: EnergyType): Int = when (type) {
        EnergyType.PETROL -> R.string.energy_petrol
        EnergyType.DIESEL -> R.string.energy_diesel
        EnergyType.CNG -> R.string.energy_cng
        EnergyType.LPG -> R.string.energy_lpg
        EnergyType.ELECTRIC -> R.string.energy_electric
        EnergyType.ETHANOL_FLEX -> R.string.energy_ethanol
    }

    @StringRes
    fun fuelSetup(setup: FuelSetup): Int = when (setup) {
        FuelSetup.Petrol -> R.string.energy_petrol
        FuelSetup.Diesel -> R.string.energy_diesel
        FuelSetup.CngPetrol -> R.string.fuel_setup_cng_petrol
        FuelSetup.Electric -> R.string.energy_electric
        FuelSetup.LpgPetrol -> R.string.fuel_setup_lpg_petrol
        FuelSetup.EthanolFlex -> R.string.fuel_setup_other
    }

    /** Data colour for a fuel (docs/design.md §4.1). Never for buttons. */
    fun energyColor(type: EnergyType): Color = when (type) {
        EnergyType.PETROL -> EnergyColors.Petrol
        EnergyType.DIESEL -> EnergyColors.Diesel
        EnergyType.CNG -> EnergyColors.Cng
        EnergyType.LPG -> EnergyColors.Lpg
        EnergyType.ELECTRIC -> EnergyColors.Electric
        EnergyType.ETHANOL_FLEX -> EnergyColors.Premium
    }

    @StringRes
    fun unit(unit: QuantityUnit): Int = when (unit) {
        QuantityUnit.LITRE -> R.string.unit_litre
        QuantityUnit.KILOGRAM -> R.string.unit_kg
        QuantityUnit.KILOWATT_HOUR -> R.string.unit_kwh
    }
}

/** "Diesel · 50 L", or just "Diesel" when capacity is unknown. */
@Composable
fun tankLabel(tank: Tank): String {
    val fuel = stringResource(VehicleLabels.energy(tank.energyType))
    val capacity = tank.capacity ?: return fuel
    val unit = stringResource(VehicleLabels.unit(tank.energyType.canonicalUnit))
    return "$fuel · ${IndianNumberFormat.decimal(capacity, if (capacity % 1.0 == 0.0) 0 else 1)} $unit"
}

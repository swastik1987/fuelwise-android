package app.fuelwise.core.ui.price

import androidx.annotation.StringRes
import app.fuelwise.core.model.PriceFuel
import app.fuelwise.core.ui.R
import app.fuelwise.core.ui.vehicle.VehicleLabels

/** Names and units for city price fuels (PRD F-09), shared by Prices, Home, the refuel sheet and notifications. */
object PriceLabels {
    @StringRes
    fun fuel(fuel: PriceFuel): Int = when (fuel) {
        PriceFuel.Petrol -> R.string.energy_petrol
        PriceFuel.Diesel -> R.string.energy_diesel
        PriceFuel.Cng -> R.string.energy_cng
        PriceFuel.PremiumPetrol -> R.string.price_fuel_premium
    }

    /** "L" or "kg". */
    @StringRes
    fun unit(fuel: PriceFuel): Int = VehicleLabels.unit(fuel.unit)
}

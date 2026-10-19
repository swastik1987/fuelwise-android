package app.fuelwise.core.model

import java.time.LocalDate

/** The fuels a city price list carries (PRD F-09.1). Prices are per [unit]: ₹/L, or ₹/kg for CNG. */
enum class PriceFuel(val unit: QuantityUnit) {
    Petrol(QuantityUnit.LITRE),
    Diesel(QuantityUnit.LITRE),
    Cng(QuantityUnit.KILOGRAM),
    PremiumPetrol(QuantityUnit.LITRE),
}

/** Who set a city price. A price the user typed always wins over the feed for the same day. */
enum class FuelPriceOrigin { Manual, Feed }

/** One city's retail selling price for one fuel on one day (PRD F-09.1). Money in paise (CLAUDE.md rule 1). */
data class FuelPrice(
    val id: String,
    val cityId: String,
    val fuel: PriceFuel,
    val date: LocalDate,
    val price: Money,
    val origin: FuelPriceOrigin = FuelPriceOrigin.Manual,
    val updatedAtUtcMs: Long = 0,
)

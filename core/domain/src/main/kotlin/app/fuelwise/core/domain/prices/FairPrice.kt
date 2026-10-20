package app.fuelwise.core.domain.prices

import app.fuelwise.core.domain.insights.PricePoint
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The city price list a tank's fill compares with. Premium-grade petrol uses the premium price; LPG, EV and flex
 * fuel have no city price in R1.
 */
fun priceFuelFor(energy: EnergyType, grade: FuelGrade? = null): PriceFuel? = when (energy) {
    EnergyType.PETROL -> if (grade == FuelGrade.Premium) PriceFuel.PremiumPetrol else PriceFuel.Petrol
    EnergyType.DIESEL -> PriceFuel.Diesel
    EnergyType.CNG -> PriceFuel.Cng
    EnergyType.LPG, EnergyType.ELECTRIC, EnergyType.ETHANOL_FLEX -> null
}

/** "Did I pay fair?" (PRD F-09.3): what was paid per unit against the city price for that day. */
data class FairPrice(val paid: Money, val city: FuelPrice) {
    /** Paid − city, per unit. Positive: above the city price. */
    val delta: Money get() = paid - city.price

    val verdict: Verdict
        get() = when {
            delta.paise > 0 -> Verdict.Above
            delta.paise < 0 -> Verdict.Below
            else -> Verdict.Same
        }

    enum class Verdict { Above, Below, Same }

    companion object {
        /**
         * A city price "exists for that date" when one was in effect on [date]: the newest on or before it, no older
         * than [MAX_AGE_DAYS]. Older manual prices are too stale to judge a fill by. [cityPrices] are one city and
         * fuel; null when nothing qualifies.
         */
        fun of(paid: Money, date: LocalDate, cityPrices: List<FuelPrice>): FairPrice? =
            priceInEffect(cityPrices, date)?.let { FairPrice(paid, it) }

        fun priceInEffect(cityPrices: List<FuelPrice>, date: LocalDate): FuelPrice? = cityPrices
            .filter { !it.date.isAfter(date) && !it.date.isBefore(date.minusDays(MAX_AGE_DAYS)) }
            .maxByOrNull { it.date }

        const val MAX_AGE_DAYS = 7L
    }
}

/** Where the fill-up sheet's price default comes from (PRD F-02.3). */
sealed interface PriceDefault {
    val price: Money

    data class LastPaid(override val price: Money) : PriceDefault

    data class City(override val price: Money, val cityPrice: FuelPrice) : PriceDefault
}

/**
 * The price/unit default: the last price paid for the tank, unless the home-city price is at least as new, in which
 * case the city price is used and tagged (PRD F-02.3). A future-dated city price never applies.
 */
fun choosePriceDefault(
    lastPaid: Money?,
    lastPaidOn: LocalDate?,
    latestCity: FuelPrice?,
    today: LocalDate,
): PriceDefault? {
    val city = latestCity?.takeIf { !it.date.isAfter(today) }
    return when {
        city != null && (lastPaidOn == null || !city.date.isBefore(lastPaidOn)) -> PriceDefault.City(city.price, city)
        lastPaid != null -> PriceDefault.LastPaid(lastPaid)
        else -> null
    }
}

/**
 * The home-city price in effect on each fill's day, for the Insights "Price paid" overlay (PRD F-06, F-09). Aligned
 * with [points]; null where no price qualifies (the same 7-day rule as "Did I pay fair?"). [cityPrices] are one city
 * and fuel.
 */
fun cityPriceOverlay(points: List<PricePoint>, cityPrices: List<FuelPrice>, zone: ZoneId): List<Money?> =
    points.map { point ->
        FairPrice.priceInEffect(cityPrices, Instant.ofEpochMilli(point.atUtcMs).atZone(zone).toLocalDate())?.price
    }

package app.fuelwise.core.domain.prices

import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import java.time.LocalDate
import javax.inject.Inject

/** One fuel's row on the prices screen (design.md screen 25): price, change and a 30-day line. */
data class FuelPriceRow(
    val fuel: PriceFuel,
    /** The price in effect today: the newest on or before today. Null: no price yet. */
    val latest: FuelPrice?,
    /** The price recorded before [latest], for the change. Null when [latest] is the only one. */
    val previous: FuelPrice?,
    /** One value per day over the last [SummarizeCityPrices.SPARK_DAYS] days, carried forward between prices. */
    val sparkline: List<Money>,
) {
    /** [latest] − [previous]; positive means the price went up. */
    val change: Money? get() = if (latest != null && previous != null) latest.price - previous.price else null
}

/** One city's prices, every fuel in [PriceFuel] order. */
data class CityPriceSummary(val cityId: String, val rows: List<FuelPriceRow>) {
    val hasAny: Boolean get() = rows.any { it.latest != null }

    fun row(fuel: PriceFuel): FuelPriceRow? = rows.firstOrNull { it.fuel == fuel }

    /** The newest date any fuel was priced, for "Updated 19 Oct". */
    val updatedOn: LocalDate? get() = rows.mapNotNull { it.latest?.date }.maxOrNull()
}

/**
 * Builds a city's rows from stored prices (PRD F-09.1). The change compares the price in effect today with the one
 * recorded before it: metro prices stay flat for weeks (PRD F-09 note), so "since the last revision" is the useful
 * number. Future-dated prices are ignored until their day.
 */
class SummarizeCityPrices @Inject constructor() {
    operator fun invoke(cityId: String, prices: List<FuelPrice>, today: LocalDate): CityPriceSummary {
        val forCity = prices.filter { it.cityId == cityId && !it.date.isAfter(today) }
        return CityPriceSummary(
            cityId,
            PriceFuel.entries.map { fuel ->
                val history = forCity.filter { it.fuel == fuel }.sortedBy { it.date }
                FuelPriceRow(
                    fuel = fuel,
                    latest = history.lastOrNull(),
                    previous = history.getOrNull(history.size - 2),
                    sparkline = sparkline(history, today),
                )
            },
        )
    }

    /** Daily values from the first price inside the window (or the one carried into it) up to today. */
    private fun sparkline(history: List<FuelPrice>, today: LocalDate): List<Money> {
        if (history.isEmpty()) return emptyList()
        val start = today.minusDays(SPARK_DAYS - 1L)
        var current = history.lastOrNull { it.date.isBefore(start) }?.price
        val byDate = history.associateBy { it.date }
        val values = (0 until SPARK_DAYS).mapNotNull { offset ->
            byDate[start.plusDays(offset.toLong())]?.let { current = it.price }
            current
        }
        return if (values.size < 2) emptyList() else values
    }

    companion object {
        const val SPARK_DAYS = 30
    }
}

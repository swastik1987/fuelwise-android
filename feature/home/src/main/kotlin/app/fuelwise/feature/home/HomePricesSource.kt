package app.fuelwise.feature.home

import app.fuelwise.core.data.repository.CityRepository
import app.fuelwise.core.data.repository.FuelPriceRepository
import app.fuelwise.core.data.repository.PriceSettingsRepository
import app.fuelwise.core.domain.prices.CityPriceSummary
import app.fuelwise.core.domain.prices.SummarizeCityPrices
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Home's "Fuel prices today" strip (design.md screen 5): the home city's prices. */
data class HomePrices(val cityName: String, val summary: CityPriceSummary)

class HomePricesSource @Inject constructor(
    private val settings: PriceSettingsRepository,
    private val prices: FuelPriceRepository,
    private val cities: CityRepository,
    private val summarize: SummarizeCityPrices,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    /** Null until a home city is chosen; the strip then stays off Home, which should feel short. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<HomePrices?> = settings.settings.map { it.homeCityId }.flatMapLatest { cityId ->
        if (cityId == null) {
            flowOf(null)
        } else {
            val today = LocalDate.now(clock.withZone(zoneId))
            prices.observePrices(setOf(cityId), today.minusDays(HISTORY_DAYS)).map { all ->
                val name = cities.catalog().byId(cityId)?.name ?: cityId
                HomePrices(name, summarize(cityId, all, today))
            }
        }
    }

    private companion object {
        const val HISTORY_DAYS = 400L
    }
}

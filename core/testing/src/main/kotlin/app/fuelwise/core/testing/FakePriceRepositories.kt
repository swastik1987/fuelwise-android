package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.CityRepository
import app.fuelwise.core.data.repository.FuelPriceRepository
import app.fuelwise.core.data.repository.PriceSettings
import app.fuelwise.core.data.repository.PriceSettingsRepository
import app.fuelwise.core.domain.prices.CityCatalog
import app.fuelwise.core.domain.prices.FeedPrice
import app.fuelwise.core.model.City
import app.fuelwise.core.model.Cities
import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.FuelPriceOrigin
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [FuelPriceRepository] with the same rules: one price per city, fuel and day; typed beats feed. */
class FakeFuelPriceRepository(initial: List<FuelPrice> = emptyList()) : FuelPriceRepository {
    val prices = MutableStateFlow(initial)
    private var nextId = 0

    override fun observePrices(cityIds: Set<String>, since: LocalDate): Flow<List<FuelPrice>> = prices.map { all ->
        all.filter { it.cityId in cityIds && !it.date.isBefore(since) }.sortedBy { it.date }
    }

    override suspend fun history(cityId: String, fuel: PriceFuel, from: LocalDate, to: LocalDate): List<FuelPrice> =
        prices.value.filter { it.cityId == cityId && it.fuel == fuel && it.date in from..to }.sortedBy { it.date }

    override suspend fun latestOnOrBefore(cityId: String, fuel: PriceFuel, date: LocalDate): FuelPrice? =
        prices.value.filter { it.cityId == cityId && it.fuel == fuel && !it.date.isAfter(date) }.maxByOrNull { it.date }

    override suspend fun saveManual(cityId: String, fuel: PriceFuel, date: LocalDate, price: Money): String {
        val existing = find(cityId, fuel, date)
        val id = existing?.id ?: "price-${nextId++}"
        put(FuelPrice(id, cityId, fuel, date, price, FuelPriceOrigin.Manual))
        return id
    }

    override suspend fun delete(id: String) = prices.update { all -> all.filterNot { it.id == id } }

    override suspend fun applyFeed(prices: List<FeedPrice>): Int = prices.count { p ->
        val existing = find(p.cityId, p.fuel, p.date)
        val write = existing == null || (existing.origin == FuelPriceOrigin.Feed && existing.price != p.price)
        if (write) {
            put(FuelPrice(existing?.id ?: "price-${nextId++}", p.cityId, p.fuel, p.date, p.price, FuelPriceOrigin.Feed))
        }
        write
    }

    private fun find(cityId: String, fuel: PriceFuel, date: LocalDate) =
        prices.value.firstOrNull { it.cityId == cityId && it.fuel == fuel && it.date == date }

    private fun put(price: FuelPrice) = prices.update { all -> all.filterNot { it.id == price.id } + price }
}

class FakePriceSettingsRepository(initial: PriceSettings = PriceSettings()) : PriceSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<PriceSettings> = state

    fun setFeedEnabled(enabled: Boolean) = state.update { it.copy(feedEnabled = enabled) }

    override suspend fun setHomeCity(cityId: String) = state.update { s ->
        s.copy(homeCityId = cityId, pinnedCityIds = s.pinnedCityIds - cityId)
    }

    override suspend fun setPinned(cityIds: List<String>) = state.update { s ->
        s.copy(
            pinnedCityIds = cityIds.distinct().filter { it != s.homeCityId }.take(PriceSettings.MAX_PINNED),
        )
    }

    override suspend fun setAlerts(enabled: Boolean) = state.update { it.copy(alerts = enabled) }

    override suspend fun markFeedFetched(atUtcMs: Long) = state.update { it.copy(lastFeedFetchUtcMs = atUtcMs) }
}

/** The onboarding city list as a catalog; metros are the popular chips. */
class FakeCityRepository(private val catalog: CityCatalog = SAMPLE) : CityRepository {
    override suspend fun catalog(): CityCatalog = catalog

    companion object {
        val SAMPLE = CityCatalog(1, Cities.all, Cities.popular)

        fun city(id: String): City = Cities.byId(id) ?: error("No sample city $id")
    }
}

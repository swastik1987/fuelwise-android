package app.fuelwise.feature.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.CityRepository
import app.fuelwise.core.data.repository.FuelPriceRepository
import app.fuelwise.core.data.repository.PriceSettings
import app.fuelwise.core.data.repository.PriceSettingsRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.prices.CityCatalog
import app.fuelwise.core.domain.prices.CityPriceSummary
import app.fuelwise.core.domain.prices.StationSearch
import app.fuelwise.core.domain.prices.SummarizeCityPrices
import app.fuelwise.core.model.City
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelPriceOrigin
import app.fuelwise.core.model.PriceFuel
import app.fuelwise.core.ui.format.MoneyInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A city on the screen with its rows. */
data class CityPrices(val city: City, val summary: CityPriceSummary)

data class PricesUiState(
    val loading: Boolean = true,
    val catalog: CityCatalog = CityCatalog.EMPTY,
    /** Null: no home city chosen yet. */
    val home: CityPrices? = null,
    val pinned: List<CityPrices> = emptyList(),
    val alerts: Boolean = false,
    /** False until a feed source exists (PRD D2/D3): prices are typed by the user. */
    val feedEnabled: Boolean = false,
    /** The selected vehicle's fuel, for "Find a petrol pump" (CNG → "CNG station near me"). */
    val energy: EnergyType? = null,
    val today: LocalDate = LocalDate.MIN,
) {
    val canPin: Boolean get() = home != null && pinned.size < PriceSettings.MAX_PINNED

    /** True when any shown price came from the feed, for the "Updated 06:30 IST" caption. */
    fun fromFeed(prices: CityPrices): Boolean =
        prices.summary.rows.any { it.latest?.origin == FuelPriceOrigin.Feed }
}

/** One-off events: open Maps with a search (PRD F-09.4). */
sealed interface PricesEvent {
    data class OpenMaps(val url: String) : PricesEvent
}

/** Fuel prices (PRD F-09, design.md screen 25). Orchestration only; the numbers come from core:domain. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PricesViewModel @Inject constructor(
    private val settings: PriceSettingsRepository,
    private val prices: FuelPriceRepository,
    cities: CityRepository,
    vehicles: VehicleRepository,
    userData: UserDataRepository,
    summarize: SummarizeCityPrices,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    private val events = Channel<PricesEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val energy = combine(vehicles.observeVehicles(), userData.userData) { list, data ->
        (list.firstOrNull { it.id == data.selectedVehicleId } ?: list.firstOrNull())?.primaryTank?.energyType
    }

    val state: StateFlow<PricesUiState> = combine(
        flow { emit(cities.catalog()) },
        settings.settings,
        energy,
    ) { catalog, s, fuel -> Triple(catalog, s, fuel) }
        .flatMapLatest { (catalog, s, fuel) ->
            val today = today()
            val since = today.minusDays(HISTORY_DAYS)
            prices.observePrices(s.followedCityIds, since).map { all ->
                fun pricesFor(id: String) = catalog.byId(id)?.let { CityPrices(it, summarize(id, all, today)) }
                PricesUiState(
                    loading = false,
                    catalog = catalog,
                    home = s.homeCityId?.let(::pricesFor),
                    pinned = s.pinnedCityIds.mapNotNull(::pricesFor),
                    alerts = s.alerts,
                    feedEnabled = s.feedEnabled,
                    energy = fuel,
                    today = today,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PricesUiState())

    fun setHome(cityId: String) {
        viewModelScope.launch { settings.setHomeCity(cityId) }
    }

    fun pin(cityId: String) {
        viewModelScope.launch {
            val current = settings.settings.first().pinnedCityIds
            settings.setPinned(current + cityId)
        }
    }

    fun unpin(cityId: String) {
        viewModelScope.launch {
            settings.setPinned(settings.settings.first().pinnedCityIds - cityId)
        }
    }

    /** Saves a typed price; a blank or zero amount is ignored. Returns false when there was nothing to save. */
    fun savePrice(cityId: String, fuel: PriceFuel, date: LocalDate, text: String): Boolean {
        val money = MoneyInput.parse(text)?.takeIf { it.paise > 0 } ?: return false
        viewModelScope.launch { prices.saveManual(cityId, fuel, minOf(date, today()), money) }
        return true
    }

    fun deletePrice(id: String) {
        viewModelScope.launch { prices.delete(id) }
    }

    fun setAlerts(enabled: Boolean) {
        viewModelScope.launch { settings.setAlerts(enabled) }
    }

    fun findPump() {
        viewModelScope.launch { events.send(PricesEvent.OpenMaps(StationSearch.mapsUrl(state.value.energy))) }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zoneId))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Enough for the 30-day line and a price set months ago; older prices are too stale to show. */
        const val HISTORY_DAYS = 400L
    }
}

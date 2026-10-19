package app.fuelwise.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.LogbookRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.domain.insights.ComputeMilestones
import app.fuelwise.core.domain.insights.Milestone
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.domain.summary.ComputeVehicleSummary
import app.fuelwise.core.domain.summary.VehicleSummary
import app.fuelwise.core.domain.trip.MonthTrips
import app.fuelwise.core.domain.trip.monthTrips
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data object NoVehicles : HomeUiState

    data class Ready(
        val vehicle: Vehicle,
        val vehicles: List<Vehicle>,
        val summary: VehicleSummary,
        val recent: List<LogbookEntry>,
        val categories: Map<String, Category>,
        val kmPerUnit: Map<String, Double>,
        val hideAmounts: Boolean,
        /** Short name of last month, for "▼ 6% vs Sep". */
        val previousMonthLabel: String,
        /** A milestone earned and not yet celebrated (PRD F-17). */
        val milestone: Milestone? = null,
        val care: HomeCare = HomeCare(),
        /** This month's finished trips for the vehicle (Home trips tile). */
        val trips: MonthTrips = MonthTrips.NONE,
        /** The home city's prices for the strip; null without a home city (PRD F-09.1). */
        val prices: HomePrices? = null,
    ) : HomeUiState
}

// Home reads every part of a vehicle's history (fills, costs, trips, reminders), so it lists more collaborators than
// the detekt limit; a holder object would only hide them.
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    vehicles: VehicleRepository,
    private val userData: UserDataRepository,
    entries: EntryRepository,
    trips: TripRepository,
    logbook: LogbookRepository,
    categories: CategoryRepository,
    summarize: ComputeVehicleSummary,
    lookupIntervals: LookupIntervals,
    milestones: ComputeMilestones,
    private val care: HomeCareSource,
    private val homePrices: HomePricesSource,
    zoneId: ZoneId,
    clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(vehicles.observeVehicles(), userData.userData) { list, data ->
        // A deleted or archived selection falls back to the first vehicle.
        Triple(list, list.firstOrNull { it.id == data.selectedVehicleId } ?: list.firstOrNull(), data)
    }.flatMapLatest { (list, selected, data) ->
        if (selected == null) {
            flowOf(HomeUiState.NoVehicles)
        } else {
            combine(
                combine(
                    entries.observeRefuels(selected.id),
                    entries.observeExpenses(selected.id),
                    trips.observeTrips(),
                    ::Triple,
                ),
                categories.observeCategories(),
                logbook.recentEntries(selected.id, RECENT_COUNT),
                care.observe(selected.id),
                homePrices.observe(),
            ) { (refuels, expenses, tripList), cats, recent, homeCare, priceStrip ->
                HomeUiState.Ready(
                    vehicle = selected,
                    vehicles = list,
                    summary = summarize(selected, refuels, expenses, cats, zoneId, tripList),
                    recent = recent,
                    categories = cats.associateBy { it.id },
                    kmPerUnit = lookupIntervals.kmPerUnit(refuels),
                    hideAmounts = data.hideAmounts,
                    previousMonthLabel = YearMonth.now(clock.withZone(zoneId)).minusMonths(1).month
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    milestone = milestones(VehicleHistory(selected, refuels, expenses)).firstOrNull {
                        it.key !in
                            data.shownMilestones
                    },
                    care = homeCare,
                    trips = monthTrips(tripList, selected.id, YearMonth.now(clock.withZone(zoneId)), zoneId),
                    prices = priceStrip,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState.Loading)

    fun selectVehicle(id: String?) {
        viewModelScope.launch { userData.setSelectedVehicle(id) }
    }

    fun setHideAmounts(hide: Boolean) {
        viewModelScope.launch { userData.setHideAmounts(hide) }
    }

    fun confirmAutoEntries() {
        val ready = uiState.value as? HomeUiState.Ready ?: return
        viewModelScope.launch { care.confirm(ready.care.unconfirmed) }
    }

    fun dismissMilestone(key: String) {
        viewModelScope.launch { userData.markMilestoneShown(key) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECENT_COUNT = 3
    }
}

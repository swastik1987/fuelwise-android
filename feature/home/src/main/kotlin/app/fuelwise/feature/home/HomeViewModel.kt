package app.fuelwise.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.LogbookRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.domain.summary.ComputeVehicleSummary
import app.fuelwise.core.domain.summary.VehicleSummary
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
    ) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    vehicles: VehicleRepository,
    private val userData: UserDataRepository,
    entries: EntryRepository,
    logbook: LogbookRepository,
    categories: CategoryRepository,
    summarize: ComputeVehicleSummary,
    lookupIntervals: LookupIntervals,
    zoneId: ZoneId,
    clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(vehicles.observeVehicles(), userData.userData) { list, data ->
        // A deleted or archived selection falls back to the first vehicle.
        Triple(list, list.firstOrNull { it.id == data.selectedVehicleId } ?: list.firstOrNull(), data.hideAmounts)
    }.flatMapLatest { (list, selected, hideAmounts) ->
        if (selected == null) {
            flowOf(HomeUiState.NoVehicles)
        } else {
            combine(
                entries.observeRefuels(selected.id),
                entries.observeExpenses(selected.id),
                categories.observeCategories(),
                logbook.recentEntries(selected.id, RECENT_COUNT),
            ) { refuels, expenses, cats, recent ->
                HomeUiState.Ready(
                    vehicle = selected,
                    vehicles = list,
                    summary = summarize(selected, refuels, expenses, cats, zoneId),
                    recent = recent,
                    categories = cats.associateBy { it.id },
                    kmPerUnit = lookupIntervals.kmPerUnit(refuels),
                    hideAmounts = hideAmounts,
                    previousMonthLabel = YearMonth.now(clock.withZone(zoneId)).minusMonths(1).month
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECENT_COUNT = 3
    }
}

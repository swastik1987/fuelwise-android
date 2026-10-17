package app.fuelwise.feature.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EntryDetailUiState {
    data object Loading : EntryDetailUiState

    data object Gone : EntryDetailUiState

    data class FillUp(
        val refuel: Refuel,
        val vehicle: Vehicle?,
        val result: IntervalResult?,
        /** km since the previous fill of the same tank. */
        val distanceSincePrevious: Double?,
    ) : EntryDetailUiState

    data class Cost(val expense: Expense, val vehicle: Vehicle?, val category: Category?) : EntryDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val entries: EntryRepository,
    vehicles: VehicleRepository,
    categories: CategoryRepository,
    lookupIntervals: LookupIntervals,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<EntryDetailRoute>()
    private val source = EntrySource.valueOf(route.source)

    val uiState: StateFlow<EntryDetailUiState> = when (source) {
        EntrySource.Refuel -> entries.observeRefuel(route.id).flatMapLatest { refuel ->
            if (refuel == null) {
                flowOf(EntryDetailUiState.Gone)
            } else {
                combine(entries.observeRefuels(refuel.vehicleId), vehicles.observeVehicle(refuel.vehicleId)) {
                        all,
                        vehicle,
                    ->
                    val tankFills = all.filter { it.tankId == refuel.tankId }
                    val previous = tankFills.filter { it.odometerKm < refuel.odometerKm }.maxByOrNull { it.odometerKm }
                    EntryDetailUiState.FillUp(
                        refuel = refuel,
                        vehicle = vehicle,
                        result = lookupIntervals(tankFills)[refuel.id],
                        distanceSincePrevious = previous?.let { refuel.odometerKm - it.odometerKm },
                    )
                }
            }
        }

        EntrySource.Expense -> entries.observeExpense(route.id).flatMapLatest { expense ->
            if (expense == null) {
                flowOf(EntryDetailUiState.Gone)
            } else {
                combine(vehicles.observeVehicle(expense.vehicleId), categories.observeCategories()) { vehicle, cats ->
                    EntryDetailUiState.Cost(expense, vehicle, cats.firstOrNull { it.id == expense.categoryId })
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryDetailUiState.Loading)

    private val deletedChannel = Channel<Unit>(Channel.BUFFERED)
    val deleted = deletedChannel.receiveAsFlow()

    fun delete() {
        viewModelScope.launch {
            when (source) {
                EntrySource.Refuel -> entries.deleteRefuel(route.id)
                EntrySource.Expense -> entries.deleteExpense(route.id)
            }
            deletedChannel.send(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

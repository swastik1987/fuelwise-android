package app.fuelwise.feature.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.DeletedTrip
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.groupByWeek
import app.fuelwise.core.domain.trip.tripCost
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A trip in the list: its fuel estimate plus linked tolls and parking. [vehicleName] only with several vehicles. */
data class TripRow(val trip: Trip, val cost: Money?, val vehicleName: String?)

data class TripWeekUi(
    val start: LocalDate,
    val end: LocalDate,
    val km: Double,
    val cost: Money,
    val rows: List<TripRow>,
)

data class TripsUiState(
    val loading: Boolean = true,
    val hasVehicles: Boolean = false,
    val weeks: List<TripWeekUi> = emptyList(),
)

sealed interface TripsEvent {
    data class Deleted(val token: DeletedTrip) : TripsEvent
}

/** Trips list (design.md screen 17; PRD F-07.1, F-07.7 weekly view). */
@HiltViewModel
class TripsViewModel @Inject constructor(
    private val trips: TripRepository,
    vehicles: VehicleRepository,
    entries: EntryRepository,
    zoneId: ZoneId,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val state: StateFlow<TripsUiState> = combine(
        trips.observeTrips(),
        vehicles.observeVehicles(),
        entries.observeAllExpenses(),
    ) { all, vehicleList, expenses ->
        val names = vehicleList.associate { it.id to it.name }
        val rows = all.map { trip ->
            TripRow(trip, tripCost(trip, expenses), names[trip.vehicleId]?.takeIf { vehicleList.size > 1 })
        }
        TripsUiState(
            loading = false,
            hasVehicles = vehicleList.isNotEmpty(),
            weeks = groupByWeek(rows, zoneId) { it.trip.startUtcMs }.map { week ->
                TripWeekUi(
                    week.start,
                    week.end,
                    week.trips.sumOf { it.trip.distanceKm },
                    week.trips.mapNotNull { it.cost }.sum(),
                    week.trips,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripsUiState())

    private val eventChannel = Channel<TripsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        // The detail screen asks for a delete; doing it here lets the list offer undo (PRD F-02 AC: 10 s).
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(PENDING_DELETE_TRIP_ID, null).filterNotNull().collect { id ->
                savedStateHandle[PENDING_DELETE_TRIP_ID] = null
                eventChannel.send(TripsEvent.Deleted(trips.delete(id)))
            }
        }
    }

    fun undoDelete(token: DeletedTrip) {
        viewModelScope.launch { trips.undoDelete(token) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

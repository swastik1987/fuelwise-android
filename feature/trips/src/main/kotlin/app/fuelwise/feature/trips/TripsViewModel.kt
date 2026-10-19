package app.fuelwise.feature.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.DeletedTrip
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.groupByWeek
import app.fuelwise.core.domain.trip.tripCost
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.location.TripRecorder
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripStatus
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
import kotlinx.coroutines.flow.first
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

/** The GPS trip recording now, for the banner that leads back to the live screen. */
data class RecordingBanner(val tripId: String, val km: Double, val paused: Boolean)

data class TripsUiState(
    val loading: Boolean = true,
    val hasVehicles: Boolean = false,
    val weeks: List<TripWeekUi> = emptyList(),
    val recording: RecordingBanner? = null,
    /** GPS trips a crash, reboot or revoked permission cut short (PRD F-07.3): resume or finish. */
    val interrupted: List<Trip> = emptyList(),
)

sealed interface TripsEvent {
    data class Deleted(val token: DeletedTrip) : TripsEvent

    data object OpenLive : TripsEvent

    data class OpenTrip(val tripId: String) : TripsEvent

    /** The trip was too short to keep and was discarded. */
    data object TooShort : TripsEvent
}

/** Trips list (design.md screen 17; PRD F-07.1, F-07.7 weekly view). */
@HiltViewModel
class TripsViewModel @Inject constructor(
    private val trips: TripRepository,
    private val vehicles: VehicleRepository,
    entries: EntryRepository,
    private val userData: UserDataRepository,
    private val recorder: TripRecorder,
    zoneId: ZoneId,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val state: StateFlow<TripsUiState> = combine(
        trips.observeTrips(),
        vehicles.observeVehicles(),
        entries.observeAllExpenses(),
        trips.observeUnfinished(),
        recorder.state,
    ) { all, vehicleList, expenses, unfinished, recording ->
        val names = vehicleList.associate { it.id to it.name }
        val active = recording as? RecordingState.Active
        val rows = all.filter { it.status == TripStatus.Done }.map { trip ->
            TripRow(trip, tripCost(trip, expenses), names[trip.vehicleId]?.takeIf { vehicleList.size > 1 })
        }
        TripsUiState(
            loading = false,
            hasVehicles = vehicleList.isNotEmpty(),
            recording = active?.let { RecordingBanner(it.tripId, it.summary.distanceKm, it.paused) },
            // Unfinished but not in this process's recorder: the process died while it recorded.
            interrupted = unfinished.filter { it.id != active?.tripId },
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

    /** Starts a GPS trip for the selected vehicle; the screen has already got location permission. */
    fun startTrip() {
        viewModelScope.launch {
            val all = vehicles.observeVehicles().first()
            val selected = userData.userData.first().selectedVehicleId
            val vehicle = all.firstOrNull { it.id == selected } ?: all.firstOrNull() ?: return@launch
            recorder.start(vehicle.id)
            eventChannel.send(TripsEvent.OpenLive)
        }
    }

    fun resumeInterrupted(tripId: String) {
        viewModelScope.launch {
            recorder.resumeInterrupted(tripId)
            eventChannel.send(TripsEvent.OpenLive)
        }
    }

    fun finishInterrupted(tripId: String) {
        viewModelScope.launch {
            val saved = recorder.finishInterrupted(tripId)
            eventChannel.send(if (saved != null) TripsEvent.OpenTrip(saved) else TripsEvent.TooShort)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

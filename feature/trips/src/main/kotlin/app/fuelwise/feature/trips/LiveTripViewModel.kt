package app.fuelwise.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.EstimateTripCost
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.location.TripRecorder
import app.fuelwise.core.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveTripUiState(
    val loading: Boolean = true,
    /** Null when nothing is recording (stopped from the notification, say). */
    val recording: RecordingState.Active? = null,
    val vehicleName: String? = null,
    val estimate: Money? = null,
    val stopping: Boolean = false,
)

sealed interface LiveTripEvent {
    /** Saved: open its detail. */
    data class Finished(val tripId: String) : LiveTripEvent

    /** Too short to keep; nothing was saved. */
    data object TooShort : LiveTripEvent
}

/** Live trip (design.md screen 18; PRD F-07.2). The recording itself lives in [TripRecorder]. */
@HiltViewModel
class LiveTripViewModel @Inject constructor(
    private val recorder: TripRecorder,
    vehicles: VehicleRepository,
    entries: EntryRepository,
    private val estimateCost: EstimateTripCost,
) : ViewModel() {
    private val stopping = MutableStateFlow(false)

    val state: StateFlow<LiveTripUiState> = combine(
        recorder.state,
        vehicles.observeVehicles(),
        entries.observeAllRefuels(),
        stopping,
    ) { recording, vehicleList, refuels, isStopping ->
        val active = recording as? RecordingState.Active
        val vehicle = vehicleList.firstOrNull { it.id == active?.vehicleId }
        LiveTripUiState(
            loading = false,
            recording = active,
            vehicleName = vehicle?.name,
            estimate = active?.let { a -> vehicle?.let { estimateCost(it, refuels, a.summary.distanceKm)?.fuelCost } },
            stopping = isStopping,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LiveTripUiState())

    private val eventChannel = Channel<LiveTripEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun pause() = recorder.pause()

    fun resume() = recorder.resume()

    /** While the screen is visible, ask for held fixes so the numbers move; batching resumes when it's hidden. */
    fun flush() = recorder.flush()

    fun stop() {
        if (stopping.value) return
        stopping.value = true
        viewModelScope.launch {
            val id = recorder.stop()
            eventChannel.send(if (id != null) LiveTripEvent.Finished(id) else LiveTripEvent.TooShort)
            stopping.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

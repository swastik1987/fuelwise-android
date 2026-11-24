package app.fuelwise.feature.trips

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import app.fuelwise.core.data.repository.DeletedTrip
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.EstimateTripCost
import app.fuelwise.core.domain.trip.ExportRange
import app.fuelwise.core.domain.trip.GpxTrack
import app.fuelwise.core.domain.trip.GpxWriter
import app.fuelwise.core.domain.trip.TripRange
import app.fuelwise.core.domain.trip.days
import app.fuelwise.core.domain.trip.gpxTrackName
import app.fuelwise.core.domain.trip.groupByMonth
import app.fuelwise.core.domain.trip.tripCost
import app.fuelwise.core.domain.trip.tripsToExport
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.location.TripRecorder
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
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

/** One month of trips with its totals (user feedback, 12 Nov 2026: months, not weeks). */
data class TripMonthUi(val month: YearMonth, val km: Double, val cost: Money, val rows: List<TripRow>)

/** The date filter: a preset, or [TripRange.Custom] with the picked days. */
data class TripFilter(val range: TripRange = TripRange.All, val from: LocalDate? = null, val to: LocalDate? = null)

/** The GPS trip recording now, for the banner that leads back to the live screen. */
data class RecordingBanner(val tripId: String, val km: Double, val paused: Boolean)

data class TripsUiState(
    val loading: Boolean = true,
    val hasVehicles: Boolean = false,
    val months: List<TripMonthUi> = emptyList(),
    val filter: TripFilter = TripFilter(),
    /** Finished trips exist, but none in the filter's dates. */
    val filteredOut: Boolean = false,
    val recording: RecordingBanner? = null,
    /** GPS trips a crash, reboot or revoked permission cut short (PRD F-07.3): resume or finish. */
    val interrupted: List<Trip> = emptyList(),
    /** "Record trips automatically" (design.md screen 17): offered while automatic trips are available but off. */
    val offerAutoTrips: Boolean = false,
    /** For "Change vehicle" on an automatic trip. */
    val vehicles: List<Vehicle> = emptyList(),
)

sealed interface TripsEvent {
    data class Deleted(val token: DeletedTrip) : TripsEvent

    data object OpenLive : TripsEvent

    data class OpenTrip(val tripId: String) : TripsEvent

    /** The trip was too short to keep and was discarded. */
    data object TooShort : TripsEvent

    /** A range export finished: [saved] trips written, or none to write (PRD F-07.8). */
    data class GpxExported(val saved: Int, val failed: Boolean = false) : TripsEvent
}

/** Trips list (design.md screen 17; PRD F-07.1, F-07.7: by month with a date filter since 12 Nov 2026). */
@HiltViewModel
class TripsViewModel @Inject constructor(
    private val trips: TripRepository,
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val userData: UserDataRepository,
    private val recorder: TripRecorder,
    autoTrips: AutoTripSettingsRepository,
    private val files: TripFileWriter,
    private val estimateCost: EstimateTripCost,
    private val clock: Clock,
    private val zoneId: ZoneId,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // A month card on Home opens Trips filtered to its month (TripsRoute.month).
    private val filter = MutableStateFlow(
        savedStateHandle.get<String>(TripsRoute::month.name)?.let(::filterFor) ?: TripFilter(),
    )

    private fun filterFor(month: String): TripFilter? {
        val picked = runCatching { YearMonth.parse(month) }.getOrNull() ?: return null
        val thisMonth = YearMonth.now(clock.withZone(zoneId))
        return when (picked) {
            thisMonth -> TripFilter(TripRange.ThisMonth)
            thisMonth.minusMonths(1) -> TripFilter(TripRange.LastMonth)
            else -> TripFilter(TripRange.Custom, picked.atDay(1), picked.atEndOfMonth())
        }
    }

    val state: StateFlow<TripsUiState> = combine(
        trips.observeTrips(),
        vehicles.observeVehicles(),
        entries.observeAllExpenses(),
        trips.observeUnfinished(),
        combine(recorder.state, autoTrips.settings, filter, ::Triple),
    ) { all, vehicleList, expenses, unfinished, (recording, auto, chosen) ->
        val names = vehicleList.associate { it.id to it.name }
        val active = recording as? RecordingState.Active
        val done = all.filter { it.status == TripStatus.Done }
        val days = chosen.range.days(LocalDate.now(clock.withZone(zoneId)), chosen.from, chosen.to)
        val shown = if (days == null) {
            done
        } else {
            done.filter { Instant.ofEpochMilli(it.startUtcMs).atZone(zoneId).toLocalDate() in days }
        }
        val rows = shown.map { trip ->
            TripRow(trip, tripCost(trip, expenses), names[trip.vehicleId]?.takeIf { vehicleList.size > 1 })
        }
        TripsUiState(
            loading = false,
            hasVehicles = vehicleList.isNotEmpty(),
            recording = active?.let { RecordingBanner(it.tripId, it.summary.distanceKm, it.paused) },
            // Unfinished but not in this process's recorder: the process died while it recorded.
            interrupted = unfinished.filter { it.id != active?.tripId },
            offerAutoTrips = auto.available && !auto.enabled,
            vehicles = vehicleList,
            months = groupByMonth(rows, zoneId) { it.trip.startUtcMs }.map { month ->
                TripMonthUi(
                    month.month,
                    month.trips.sumOf { it.trip.distanceKm },
                    month.trips.mapNotNull { it.cost }.sum(),
                    month.trips,
                )
            },
            filter = chosen,
            filteredOut = done.isNotEmpty() && shown.isEmpty(),
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

    /** "Not my drive" on an automatic trip (PRD F-21.3): deleted, with the usual undo. */
    fun notMyDrive(tripId: String) {
        viewModelScope.launch { eventChannel.send(TripsEvent.Deleted(trips.delete(tripId))) }
    }

    /**
     * "Change vehicle" on an automatic trip (PRD F-21.3). The fuel estimate is redone for the new vehicle; the
     * corrected trip also teaches the time-of-day habit used to assign the next one.
     */
    fun changeVehicle(tripId: String, vehicleId: String) {
        viewModelScope.launch {
            val trip = trips.observeTrip(tripId).first() ?: return@launch
            val vehicle = vehicles.observeVehicles().first().firstOrNull { it.id == vehicleId } ?: return@launch
            val estimate = estimateCost(vehicle, entries.observeAllRefuels().first(), trip.distanceKm)
            trips.save(trip.copy(vehicleId = vehicleId, estimatedFuelCost = estimate?.fuelCost))
        }
    }

    /** A preset; picking Custom goes through [selectDays]. */
    fun selectRange(range: TripRange) {
        filter.value = TripFilter(range)
    }

    fun selectDays(from: LocalDate, to: LocalDate) {
        filter.value = TripFilter(TripRange.Custom, minOf(from, to), maxOf(from, to))
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

    /** Writes the GPS trips in [range] as one GPX file with a track each (PRD F-07.8). */
    fun exportRange(uri: Uri, range: ExportRange) {
        viewModelScope.launch {
            val tracks = tripsToExport(trips.observeTrips().first(), range, clock.millis(), zoneId)
                .map { GpxTrack(gpxTrackName(it, ZoneId.of(it.zoneId)), trips.route(it.id)) }
                .filter { it.points.isNotEmpty() }
            if (tracks.isEmpty()) {
                eventChannel.send(TripsEvent.GpxExported(saved = 0))
                return@launch
            }
            val saved = files.write(uri, GpxWriter.write(tracks))
            eventChannel.send(TripsEvent.GpxExported(if (saved) tracks.size else 0, failed = !saved))
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

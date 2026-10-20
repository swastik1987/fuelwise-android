package app.fuelwise.feature.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.EstimateTripCost
import app.fuelwise.core.domain.trip.TripCostEstimate
import app.fuelwise.core.domain.trip.TripDistance
import app.fuelwise.core.domain.trip.resolveTripDistance
import app.fuelwise.core.domain.units.UnitSettings
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.format.NumberInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DistanceError { Missing, EndNotAfterStart }

/** What the manual trip form holds, as typed. */
data class TripFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val saving: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicle: Vehicle? = null,
    /** Display units (PRD F-18), app-wide and per vehicle, for the estimate line. */
    val unitSettings: UnitSettings = UnitSettings(),
    val startUtcMs: Long = 0L,
    val startOdometer: String = "",
    val endOdometer: String = "",
    val distance: String = "",
    val purpose: TripPurpose = TripPurpose.Personal,
    val customPurpose: String = "",
    val fromPlace: String = "",
    val toPlace: String = "",
    val notes: String = "",
    /** Distance from both odometer readings, shown in place of the typed distance. */
    val odometerDistanceKm: Double? = null,
    val estimate: TripCostEstimate? = null,
    val distanceError: DistanceError? = null,
)

/** Add or edit a manual trip (PRD F-07.1). */
@HiltViewModel
class TripEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trips: TripRepository,
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val userData: UserDataRepository,
    private val estimateCost: EstimateTripCost,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TripEditorRoute>()
    private var editing: Trip? = null
    private var refuels: List<Refuel> = emptyList()

    private val _state = MutableStateFlow(TripFormState(startUtcMs = clock.millis(), isEdit = route.tripId != null))
    val state: StateFlow<TripFormState> = _state.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val all = vehicles.observeVehicles().first()
        val existing = route.tripId?.let { trips.observeTrip(it).first() }
        editing = existing
        refuels = entries.observeAllRefuels().first()
        val user = userData.userData.first()
        val vehicleId = existing?.vehicleId ?: user.selectedVehicleId
        val vehicle = all.firstOrNull { it.id == vehicleId } ?: all.firstOrNull()
        _state.update { s ->
            val typedDistance = existing?.takeIf { it.startOdometerKm == null || it.endOdometerKm == null }
            s.copy(
                loading = false,
                vehicles = all,
                vehicle = vehicle,
                unitSettings = user.units,
                startUtcMs = existing?.startUtcMs ?: s.startUtcMs,
                startOdometer = existing?.startOdometerKm?.let(NumberInput::plain).orEmpty(),
                endOdometer = existing?.endOdometerKm?.let(NumberInput::plain).orEmpty(),
                distance = typedDistance?.distanceKm?.let(NumberInput::plain).orEmpty(),
                purpose = existing?.purpose ?: TripPurpose.Personal,
                customPurpose = existing?.customPurpose.orEmpty(),
                fromPlace = existing?.fromPlace.orEmpty(),
                toPlace = existing?.toPlace.orEmpty(),
                notes = existing?.notes.orEmpty(),
            ).recomputed()
        }
    }

    fun onVehicle(id: String?) {
        val v = _state.value.vehicles.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(vehicle = v).recomputed() }
    }

    fun onDateTime(utcMs: Long) = _state.update { it.copy(startUtcMs = utcMs) }

    fun onStartOdometer(text: String) = _state.update {
        it.copy(startOdometer = NumberInput.sanitize(text, 1).take(MAX_NUMBER)).recomputed(clearError = true)
    }

    fun onEndOdometer(text: String) = _state.update {
        it.copy(endOdometer = NumberInput.sanitize(text, 1).take(MAX_NUMBER)).recomputed(clearError = true)
    }

    fun onDistance(text: String) = _state.update {
        it.copy(distance = NumberInput.sanitize(text, 1).take(MAX_NUMBER)).recomputed(clearError = true)
    }

    fun onPurpose(purpose: TripPurpose) = _state.update { it.copy(purpose = purpose) }

    fun onCustomPurpose(text: String) = _state.update { it.copy(customPurpose = text.take(MAX_LABEL)) }

    fun onFrom(text: String) = _state.update { it.copy(fromPlace = text.take(MAX_LABEL)) }

    fun onTo(text: String) = _state.update { it.copy(toPlace = text.take(MAX_LABEL)) }

    fun onNotes(text: String) = _state.update { it.copy(notes = text.take(MAX_NOTES)) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val vehicle = s.vehicle ?: return
        val resolved = s.resolve()
        if (resolved !is TripDistance.Valid) {
            _state.update { it.copy(distanceError = resolved.toError()) }
            return
        }
        _state.update { it.copy(saving = true) }
        val previous = editing
        // The estimate is fixed when the trip is saved; an edit that keeps vehicle and distance keeps it.
        val unchanged = previous != null && previous.vehicleId == vehicle.id &&
            previous.distanceKm == resolved.distanceKm
        val cost = if (unchanged) previous.estimatedFuelCost else s.estimate?.fuelCost
        viewModelScope.launch {
            trips.save(
                Trip(
                    id = previous?.id.orEmpty(),
                    vehicleId = vehicle.id,
                    startUtcMs = s.startUtcMs,
                    endUtcMs = previous?.endUtcMs,
                    zoneId = previous?.zoneId ?: zoneId.id,
                    distanceKm = resolved.distanceKm,
                    startOdometerKm = resolved.startOdometerKm,
                    endOdometerKm = resolved.endOdometerKm,
                    purpose = s.purpose,
                    customPurpose = s.customPurpose.takeIf { s.purpose == TripPurpose.Custom },
                    fromPlace = s.fromPlace,
                    toPlace = s.toPlace,
                    notes = s.notes,
                    estimatedFuelCost = cost,
                    source = previous?.source ?: TripSource.Manual,
                    status = previous?.status ?: TripStatus.Done,
                ),
            )
            savedChannel.send(Unit)
        }
    }

    private fun TripFormState.resolve(): TripDistance = resolveTripDistance(
        NumberInput.parseDecimal(startOdometer),
        NumberInput.parseDecimal(endOdometer),
        NumberInput.parseDecimal(distance),
    )

    private fun TripFormState.recomputed(clearError: Boolean = false): TripFormState {
        val resolved = resolve()
        val bothOdometers = NumberInput.parseDecimal(startOdometer) != null &&
            NumberInput.parseDecimal(endOdometer) != null
        val km = (resolved as? TripDistance.Valid)?.distanceKm
        return copy(
            odometerDistanceKm = km?.takeIf { bothOdometers },
            estimate = vehicle?.let { v -> km?.let { estimateCost(v, refuels, it) } },
            distanceError = if (clearError) null else distanceError,
        )
    }

    private fun TripDistance.toError(): DistanceError = when (this) {
        TripDistance.EndNotAfterStart -> DistanceError.EndNotAfterStart
        else -> DistanceError.Missing
    }

    private companion object {
        const val MAX_NUMBER = 9
        const val MAX_LABEL = 40
        const val MAX_NOTES = 500
    }
}

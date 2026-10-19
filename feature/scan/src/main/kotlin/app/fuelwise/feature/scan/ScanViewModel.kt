package app.fuelwise.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.ScanSettingsRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.data.vault.ScanPhotoStash
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.scan.OdometerParser
import app.fuelwise.core.domain.scan.PumpMeterParser
import app.fuelwise.core.domain.scan.ReceiptParser
import app.fuelwise.core.domain.scan.TriangleReading
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.ScanPrefill
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scan (PRD F-15, design.md screen 9). Orchestration only: the camera layer sends recognised text, core:domain parses
 * it, and this decides when to show the confirm sheet. Nothing is saved here; the values go to Add fuel.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val userData: UserDataRepository,
    private val settings: ScanSettingsRepository,
    private val stash: ScanPhotoStash,
    private val clock: Clock,
    private val zoneId: ZoneId,
    @param:Dispatcher(FwDispatcher.Default) private val parsing: CoroutineDispatcher,
) : ViewModel() {
    sealed interface Event {
        data class Done(val prefill: ScanPrefill) : Event
    }

    private val route = savedStateHandle.toRoute<ScanRoute>()
    private val _state = MutableStateFlow(
        ScanUiState(mode = ScanMode.entries.firstOrNull { it.name == route.mode } ?: ScanMode.PumpMeter),
    )
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow: Flow<Event> = events.receiveAsFlow()

    private var rateHint: Money? = null
    private var lastOdometer: Double? = null
    private var busy = false
    private var stableFrames = 0
    private var lastValid: ScanPrefill? = null
    private var latestJpeg: (() -> ByteArray?)? = null
    private var reviewJpeg: (() -> ByteArray?)? = null
    private var struggleJob: Job? = null

    init {
        viewModelScope.launch { loadHints() }
        viewModelScope.launch { settings.attachPhoto.collect { a -> _state.update { it.copy(attachPhoto = a) } } }
        restartStruggleTimer()
    }

    /** The last price paid and the highest odometer for the vehicle tune the parsers; they never fill a field. */
    private suspend fun loadHints() {
        val vehicleId = route.vehicleId ?: userData.userData.first().selectedVehicleId
        val all = vehicles.observeVehicles().first()
        val vehicle = all.firstOrNull { it.id == vehicleId } ?: all.firstOrNull() ?: return
        val tank = vehicle.tanks.filter { it.energyType != EnergyType.ELECTRIC }
            .let { tanks -> tanks.firstOrNull { it.isPrimary } ?: tanks.firstOrNull() }
        val refuels = entries.observeRefuels(vehicle.id).first()
        rateHint = refuels.filter { it.tankId == tank?.id }.maxByOrNull { it.dateTimeUtcMs }?.unitPrice
        lastOdometer = refuels.maxOfOrNull { it.odometerKm }
        _state.update { it.copy(unit = tank?.energyType?.canonicalUnit ?: QuantityUnit.LITRE) }
    }

    fun onCameraAccess(granted: Boolean) =
        _state.update { it.copy(camera = if (granted) CameraAccess.Granted else CameraAccess.Denied) }

    fun onMode(mode: ScanMode) {
        if (mode == _state.value.mode) return
        resetReading()
        _state.update { it.copy(mode = mode, review = null) }
    }

    /** Called for every analysed frame; frames that arrive while one is being parsed are dropped. */
    fun onFrame(frame: OcrFrame) {
        if (busy || _state.value.review != null) return
        busy = true
        val mode = _state.value.mode
        viewModelScope.launch {
            val (live, valid) = withContext(parsing) { parse(mode, frame) }
            busy = false
            if (_state.value.review != null || _state.value.mode != mode) return@launch
            latestJpeg = frame.jpeg
            stableFrames = when {
                !valid -> 0
                live.values == lastValid -> stableFrames + 1
                else -> 1
            }
            lastValid = live.values.takeIf { valid }
            if (live.labels.isNotEmpty()) restartStruggleTimer()
            _state.update {
                it.copy(
                    live = live.values,
                    labels = live.labels,
                    frameWidth = frame.width,
                    frameHeight = frame.height,
                    allValid = valid,
                    struggling = it.struggling && live.labels.isEmpty(),
                )
            }
            if (stableFrames >= STABLE_FRAMES) openReview(validated = true)
        }
    }

    private class Live(val values: ScanPrefill, val labels: List<LiveLabel>)

    private fun parse(mode: ScanMode, frame: OcrFrame): Pair<Live, Boolean> = when (mode) {
        ScanMode.PumpMeter -> PumpMeterParser.parse(frame.lines, rateHint).let {
            it.live(ScanPrefill()) to it.validated
        }

        ScanMode.Receipt -> {
            val r = ReceiptParser.parse(frame.lines, rateHint)
            val extra = ScanPrefill(
                dateTimeUtcMs = r.dateTimeUtcMs(zoneId, clock.millis()),
                stationName = r.stationName,
            )
            r.amounts.live(extra) to r.amounts.validated
        }

        ScanMode.Odometer -> {
            val km = OdometerParser.parse(frame.lines, lastOdometer)
            val labels = listOfNotNull(km?.box?.let { LiveLabel(ScanField.Odometer, it) })
            Live(ScanPrefill(odometerKm = km?.value), labels) to (km != null)
        }
    }

    private fun TriangleReading.live(extra: ScanPrefill) = Live(
        extra.copy(total = total?.value, quantity = quantity?.value, unitPrice = unitPrice?.value),
        listOfNotNull(
            total?.box?.let { LiveLabel(ScanField.Total, it) },
            quantity?.box?.let { LiveLabel(ScanField.Quantity, it) },
            unitPrice?.box?.let { LiveLabel(ScanField.Rate, it) },
        ),
    )

    /** "Read now": review whatever was read, each field to be ticked unless the reading was consistent. */
    fun onReadNow() {
        if (!_state.value.hasReading) {
            _state.update { it.copy(struggling = true) }
            return
        }
        openReview(validated = _state.value.allValid)
    }

    private fun openReview(validated: Boolean) {
        val s = _state.value
        val fields = s.live.fields()
        reviewJpeg = latestJpeg
        struggleJob?.cancel()
        _state.update {
            it.copy(
                review = ScanReview(
                    values = s.live,
                    fields = fields,
                    // An odometer is always confirmed by pressing "Use" on its single field (PRD F-15).
                    confirmed = if (validated) fields.toSet() else emptySet(),
                    validated = validated,
                    hasPhoto = s.mode != ScanMode.Odometer && latestJpeg != null,
                ),
            )
        }
    }

    fun onToggleField(field: ScanField) = _state.update { s ->
        val review = s.review ?: return@update s
        val confirmed = if (field in review.confirmed) review.confirmed - field else review.confirmed + field
        s.copy(review = review.copy(confirmed = confirmed))
    }

    fun onAttachPhoto(attach: Boolean) {
        _state.update { it.copy(attachPhoto = attach) }
        viewModelScope.launch { settings.setAttachPhoto(attach) }
    }

    fun onRescan() {
        resetReading()
        _state.update { it.copy(review = null) }
    }

    fun onUse() {
        val s = _state.value
        val review = s.review ?: return
        if (review.confirmed.isEmpty()) return
        viewModelScope.launch {
            val photo = if (review.hasPhoto && s.attachPhoto) withContext(parsing) { reviewJpeg?.invoke() } else null
            val prefill = review.values.keep(review.confirmed).copy(photoToken = photo?.let(stash::put))
            events.send(Event.Done(prefill))
        }
    }

    private fun resetReading() {
        stableFrames = 0
        lastValid = null
        latestJpeg = null
        reviewJpeg = null
        _state.update {
            it.copy(live = ScanPrefill(), labels = emptyList(), allValid = false, struggling = false)
        }
        restartStruggleTimer()
    }

    private fun restartStruggleTimer() {
        struggleJob?.cancel()
        struggleJob = viewModelScope.launch {
            delay(STRUGGLE_AFTER_MS)
            _state.update { if (it.review == null) it.copy(struggling = true) else it }
        }
    }

    private companion object {
        /** The same consistent reading in this many frames in a row opens the confirm sheet by itself. */
        const val STABLE_FRAMES = 2
        const val STRUGGLE_AFTER_MS = 8_000L
    }
}

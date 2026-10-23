package app.fuelwise.feature.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.ObserveReminderStatuses
import app.fuelwise.core.data.repository.ReminderRepository
import app.fuelwise.core.data.repository.RulesRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.entry.gpsDistances
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.defaultsFor
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderCompletion
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.notifications.Notifier
import app.fuelwise.core.ui.format.NumberInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemindersUiState(
    val loading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val upcoming: List<ReminderStatus> = emptyList(),
    val done: List<ReminderCompletion> = emptyList(),
    /** Names of completed reminders that are gone (one-offs), for the Done tab. */
    val titles: Map<String, Reminder> = emptyMap(),
)

/** Reminders list (design.md screen 23). */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    statuses: ObserveReminderStatuses,
    reminders: ReminderRepository,
    vehicles: VehicleRepository,
    private val notifier: Notifier,
) : ViewModel() {
    /** Notifications allowed; without it reminders still show here with a banner (PRD F-11 AC). */
    fun canNotify(): Boolean = notifier.canPost

    val state: StateFlow<RemindersUiState> = combine(
        statuses(),
        reminders.observeCompletions(),
        reminders.observeReminders(),
        vehicles.observeVehicles(),
    ) { upcoming, done, all, list ->
        RemindersUiState(false, list, upcoming, done, all.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemindersUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class ReminderForm(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicle: Vehicle? = null,
    /** Null while the preset grid is showing. */
    val kind: ReminderKind? = null,
    val title: String = "",
    val dueDateUtcMs: Long? = null,
    val dueOdometer: String = "",
    val repeatMonths: String = "",
    val repeatKm: String = "",
    val notifyDaysBefore: Set<Int> = Reminder.DEFAULT_NOTIFY_DAYS.toSet(),
    val notes: String = "",
    val currentOdometerKm: Double? = null,
    /** From the injected clock, for the date picker's starting point. */
    val nowUtcMs: Long = 0,
    val missingDue: Boolean = false,
    val missingTitle: Boolean = false,
)

sealed interface ReminderEditorEvent {
    /** Saved; [firstReminder] lets the screen ask for notifications in context (PRD §9). */
    data class Saved(val firstReminder: Boolean) : ReminderEditorEvent

    data object Closed : ReminderEditorEvent
}

/** Add / edit a reminder (design.md screen 23, add sheet). */
@HiltViewModel
class ReminderEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reminders: ReminderRepository,
    private val vehicles: VehicleRepository,
    private val rules: RulesRepository,
    private val userData: UserDataRepository,
    private val entries: EntryRepository,
    private val trips: TripRepository,
    private val predictions: ComputePredictions,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ReminderEditorRoute>()
    private val _state = MutableStateFlow(ReminderForm())
    val state: StateFlow<ReminderForm> = _state.asStateFlow()

    private val eventChannel = Channel<ReminderEditorEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var existing: Reminder? = null

    init {
        viewModelScope.launch {
            val list = vehicles.observeVehicles().first()
            val existing = route.reminderId?.let { reminders.reminder(it) }.also { existing = it }
            val selectedId = existing?.vehicleId ?: route.vehicleId ?: userData.userData.first().selectedVehicleId
            val vehicle = list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
            _state.value = if (existing != null) {
                ReminderForm(
                    loading = false,
                    isEdit = true,
                    vehicles = list,
                    vehicle = vehicle,
                    kind = existing.kind,
                    title = existing.title.orEmpty(),
                    dueDateUtcMs = existing.dueDateUtcMs,
                    dueOdometer = existing.dueOdometerKm?.let { it.roundToLong().toString() }.orEmpty(),
                    repeatMonths = existing.repeatMonths?.toString().orEmpty(),
                    repeatKm = existing.repeatKm?.let { it.roundToLong().toString() }.orEmpty(),
                    notifyDaysBefore = existing.notifyDaysBefore.toSet(),
                    notes = existing.notes.orEmpty(),
                    currentOdometerKm = currentOdometer(existing.vehicleId),
                    nowUtcMs = clock.millis(),
                )
            } else {
                ReminderForm(
                    loading = false,
                    vehicles = list,
                    vehicle = vehicle,
                    currentOdometerKm = vehicle?.let { currentOdometer(it.id) },
                    nowUtcMs = clock.millis(),
                )
            }
            route.kind?.let { k -> ReminderKind.entries.firstOrNull { it.name == k }?.let(::pickKind) }
        }
    }

    /** The odometer today: predicted from recent driving, else the last reading. */
    private suspend fun currentOdometer(vehicleId: String): Double? {
        val vehicle = vehicles.observeVehicle(vehicleId).first() ?: return null
        val tripList = trips.observeTrips().first()
        val readings =
            odometerReadings(
                vehicle,
                entries.observeRefuels(vehicleId).first(),
                entries.observeExpenses(vehicleId).first(),
                tripList,
            )
        return predictions.predictedOdometer(readings, gpsDistances(vehicle, tripList))
    }

    fun pickKind(kind: ReminderKind) {
        viewModelScope.launch {
            val vehicle = _state.value.vehicle ?: return@launch
            val defaults = rules.reminderRules().defaultsFor(kind, vehicle)
            val today = LocalDate.now(clock.withZone(zoneId))
            // Maintenance runs from today; legal renewals need the date on the certificate, so they start empty.
            val fromToday = kind in MAINTENANCE
            val odo = _state.value.currentOdometerKm
            _state.update { s ->
                s.copy(
                    kind = kind,
                    repeatMonths = defaults.months?.toString().orEmpty(),
                    repeatKm = defaults.km?.let { it.roundToLong().toString() }.orEmpty(),
                    notifyDaysBefore = defaults.notifyDaysBefore.toSet(),
                    dueDateUtcMs = if (fromToday) {
                        defaults.months?.let {
                            today.plusMonths(it.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
                        }
                    } else {
                        null
                    },
                    dueOdometer = if (fromToday && odo != null) {
                        defaults.km?.let { (odo + it).roundToLong().toString() }.orEmpty()
                    } else {
                        ""
                    },
                )
            }
        }
    }

    fun backToPresets() = _state.update { it.copy(kind = null) }

    fun selectVehicle(id: String) = viewModelScope.launch {
        val vehicle = _state.value.vehicles.firstOrNull { it.id == id } ?: return@launch
        _state.update { it.copy(vehicle = vehicle, currentOdometerKm = currentOdometer(id)) }
    }

    fun onTitle(value: String) = _state.update { it.copy(title = value.take(MAX_TITLE), missingTitle = false) }

    fun onDueDate(utcMs: Long?) = _state.update { it.copy(dueDateUtcMs = utcMs, missingDue = false) }

    fun onDueOdometer(value: String) = _state.update {
        it.copy(dueOdometer = NumberInput.sanitize(value, 0), missingDue = false)
    }

    fun onRepeatMonths(value: String) = _state.update {
        it.copy(repeatMonths = value.filter(Char::isDigit).take(MAX_DIGITS))
    }

    fun onRepeatKm(value: String) = _state.update { it.copy(repeatKm = NumberInput.sanitize(value, 0)) }

    fun toggleNotify(days: Int) = _state.update { s ->
        s.copy(
            notifyDaysBefore = if (days in
                s.notifyDaysBefore
            ) {
                s.notifyDaysBefore - days
            } else {
                s.notifyDaysBefore + days
            },
        )
    }

    fun onNotes(value: String) = _state.update { it.copy(notes = value) }

    fun save() {
        val s = _state.value
        val kind = s.kind ?: return
        val vehicle = s.vehicle ?: return
        val dueOdo = NumberInput.parseDecimal(s.dueOdometer)
        val missingDue = s.dueDateUtcMs == null && dueOdo == null
        val missingTitle = kind == ReminderKind.Custom && s.title.isBlank()
        if (missingDue || missingTitle) {
            _state.update { it.copy(missingDue = missingDue, missingTitle = missingTitle) }
            return
        }
        viewModelScope.launch {
            val first = reminders.observeReminders().first().isEmpty()
            reminders.save(
                (existing ?: Reminder("", vehicle.id, kind, null, null, null)).copy(
                    vehicleId = vehicle.id,
                    kind = kind,
                    title = s.title.trim().ifEmpty { null },
                    dueDateUtcMs = s.dueDateUtcMs,
                    dueOdometerKm = dueOdo,
                    repeatMonths = s.repeatMonths.toIntOrNull()?.takeIf { it > 0 },
                    repeatKm = NumberInput.parseDecimal(s.repeatKm)?.takeIf { it > 0 },
                    notifyDaysBefore = s.notifyDaysBefore.sortedDescending(),
                    notes = s.notes.trim().ifEmpty { null },
                    snoozedUntilUtcMs = null,
                ),
            )
            eventChannel.send(ReminderEditorEvent.Saved(firstReminder = first))
        }
    }

    fun markDone() {
        val id = existing?.id ?: return
        viewModelScope.launch {
            reminders.complete(id, _state.value.currentOdometerKm)
            eventChannel.send(ReminderEditorEvent.Closed)
        }
    }

    fun delete() {
        val id = existing?.id ?: return
        viewModelScope.launch {
            reminders.delete(id)
            eventChannel.send(ReminderEditorEvent.Closed)
        }
    }

    private companion object {
        const val MAX_TITLE = 60
        const val MAX_DIGITS = 3
        val MAINTENANCE = setOf(
            ReminderKind.Service,
            ReminderKind.EngineOil,
            ReminderKind.Tyres,
            ReminderKind.Battery,
            ReminderKind.Coolant,
            ReminderKind.Wipers,
        )
    }
}

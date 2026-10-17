package app.fuelwise.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.RecurrenceRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.designsystem.component.KeypadKey
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.Recurrence
import app.fuelwise.core.model.RepeatUnit
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.format.KeypadInput
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.format.NumberInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
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

enum class ExpenseField { Amount, Odometer }

data class ExpenseFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val isToll: Boolean = false,
    val vehicle: Vehicle? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryId: String? = null,
    val amount: String = "",
    val odometer: String = "",
    val dateTimeUtcMs: Long = 0,
    val payment: PaymentMethod? = null,
    val notes: String = "",
    val plaza: String = "",
    val plazaSuggestions: List<String> = emptyList(),
    val tollByFastag: Boolean = true,
    val focus: ExpenseField? = ExpenseField.Amount,
    val amountMissing: Boolean = false,
    val categoryMissing: Boolean = false,
    val saving: Boolean = false,
    /** Repeat this expense (PRD F-04.3); only offered when adding. */
    val repeat: Boolean = false,
    val repeatEvery: String = "1",
    val repeatUnit: RepeatUnit = RepeatUnit.Months,
)

/** Add / edit an expense or a toll (PRD F-04.1, F-10.2; design.md screens 10 and 11). */
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val categories: CategoryRepository,
    private val entries: EntryRepository,
    private val userData: UserDataRepository,
    private val recurrences: RecurrenceRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ExpenseRoute>()
    private var editing: Expense? = null

    private val _state = MutableStateFlow(
        ExpenseFormState(dateTimeUtcMs = clock.millis(), isEdit = route.expenseId != null, isToll = route.toll),
    )
    val state: StateFlow<ExpenseFormState> = _state.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            entries.observeRecentPlazas().collect { p -> _state.update { it.copy(plazaSuggestions = p) } }
        }
    }

    private suspend fun load() {
        val all = vehicles.observeVehicles().first()
        val cats = categories.observeCategories().first().filterNot { it.isHidden }
        val existing = route.expenseId?.let { entries.observeExpense(it).first() }
        editing = existing
        val vehicleId = existing?.vehicleId ?: route.vehicleId ?: userData.userData.first().selectedVehicleId
        val vehicle = all.firstOrNull { it.id == vehicleId } ?: all.firstOrNull()
        val isToll = route.toll || existing?.tollPlaza != null ||
            existing?.categoryId in setOf(SystemCategory.TollFastag.id, SystemCategory.TollCash.id)
        _state.update { s ->
            s.copy(
                loading = false,
                vehicles = all,
                vehicle = vehicle,
                categories = cats,
                isToll = isToll,
                categoryId = existing?.categoryId,
                amount = existing?.amount?.let(MoneyInput::plain).orEmpty(),
                odometer = existing?.odometerKm?.let(NumberInput::plain).orEmpty(),
                dateTimeUtcMs = existing?.dateTimeUtcMs ?: s.dateTimeUtcMs,
                payment = existing?.paymentMethod,
                notes = existing?.notes.orEmpty(),
                plaza = existing?.tollPlaza.orEmpty(),
                tollByFastag = existing?.categoryId != SystemCategory.TollCash.id,
                focus = if (existing != null) null else ExpenseField.Amount,
            )
        }
    }

    fun onKey(key: KeypadKey) = _state.update { s ->
        when (s.focus) {
            ExpenseField.Amount -> s.copy(amount = KeypadInput.apply(s.amount, key, 2), amountMissing = false)
            ExpenseField.Odometer -> s.copy(odometer = KeypadInput.apply(s.odometer, key, 1))
            null -> s
        }
    }

    fun onFocus(field: ExpenseField?) = _state.update { it.copy(focus = field) }

    fun onCategory(id: String) = _state.update { it.copy(categoryId = id, categoryMissing = false) }

    fun onPayment(method: PaymentMethod) = _state.update {
        it.copy(
            payment = if (it.payment ==
                method
            ) {
                null
            } else {
                method
            },
        )
    }

    fun onNotes(text: String) = _state.update { it.copy(notes = text.take(MAX_NOTES)) }

    fun onPlaza(text: String) = _state.update { it.copy(plaza = text.take(MAX_PLAZA)) }

    fun onTollMethod(fastag: Boolean) = _state.update { it.copy(tollByFastag = fastag) }

    fun onDateTime(utcMs: Long) = _state.update { it.copy(dateTimeUtcMs = utcMs) }

    fun onVehicle(id: String?) {
        if (_state.value.isEdit) return
        val v = _state.value.vehicles.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(vehicle = v) }
        viewModelScope.launch { userData.setSelectedVehicle(v.id) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val amount = MoneyInput.parse(s.amount)?.takeIf { it.paise > 0 }
        val categoryId = when {
            s.isToll && s.tollByFastag -> SystemCategory.TollFastag.id
            s.isToll -> SystemCategory.TollCash.id
            else -> s.categoryId
        }
        _state.update { it.copy(amountMissing = amount == null, categoryMissing = categoryId == null) }
        val vehicle = s.vehicle
        if (amount == null || categoryId == null || vehicle == null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            entries.saveExpense(
                Expense(
                    id = editing?.id.orEmpty(),
                    vehicleId = vehicle.id,
                    categoryId = categoryId,
                    dateTimeUtcMs = s.dateTimeUtcMs,
                    zoneId = editing?.zoneId ?: zoneId.id,
                    amount = amount,
                    odometerKm = NumberInput.parseDecimal(s.odometer),
                    paymentMethod = if (s.isToll && s.tollByFastag) PaymentMethod.FastagWallet else s.payment,
                    notes = s.notes.ifBlank { null },
                    tollPlaza = if (s.isToll) s.plaza.ifBlank { null } else null,
                    // Editing an auto-created entry confirms it and keeps its recurrence link (idempotency).
                    recurrenceId = editing?.recurrenceId,
                    isAutoCreated = false,
                ),
            )
            if (s.repeat && editing == null) saveRecurrence(s, vehicle.id, categoryId, amount)
            savedChannel.send(Unit)
        }
    }

    /** This entry is the first occurrence; the recurrence starts at the next one, so nothing is created twice. */
    private suspend fun saveRecurrence(s: ExpenseFormState, vehicleId: String, categoryId: String, amount: Money) {
        val every = s.repeatEvery.toIntOrNull()?.coerceIn(1, MAX_REPEAT) ?: 1
        val first = Instant.ofEpochMilli(s.dateTimeUtcMs).atZone(zoneId).toLocalDate()
        val next = when (s.repeatUnit) {
            RepeatUnit.Days -> first.plusDays(every.toLong())
            RepeatUnit.Weeks -> first.plusWeeks(every.toLong())
            RepeatUnit.Months -> first.plusMonths(every.toLong())
            RepeatUnit.Years -> first.plusYears(every.toLong())
        }
        recurrences.save(
            Recurrence(
                id = "",
                vehicleId = vehicleId,
                categoryId = categoryId,
                amount = amount,
                every = every,
                unit = s.repeatUnit,
                startUtcMs = next.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                zoneId = zoneId.id,
                paymentMethod = s.payment,
                notes = s.notes.ifBlank { null },
            ),
        )
    }

    fun onRepeat(on: Boolean) = _state.update { it.copy(repeat = on) }

    fun onRepeatEvery(value: String) = _state.update {
        it.copy(repeatEvery = value.filter(Char::isDigit).take(MAX_REPEAT_DIGITS))
    }

    fun onRepeatUnit(unit: RepeatUnit) = _state.update { it.copy(repeatUnit = unit) }

    private companion object {
        const val MAX_NOTES = 500
        const val MAX_PLAZA = 60
        const val MAX_REPEAT = 365
        const val MAX_REPEAT_DIGITS = 3
    }
}

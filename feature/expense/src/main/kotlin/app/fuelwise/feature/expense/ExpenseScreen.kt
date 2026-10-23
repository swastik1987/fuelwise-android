package app.fuelwise.feature.expense

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.data.photo.EntryPhotoDraft
import app.fuelwise.core.designsystem.component.FwCheckboxRow
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwEntrySheet
import app.fuelwise.core.designsystem.component.FwHeaderChip
import app.fuelwise.core.designsystem.component.FwKeypadBar
import app.fuelwise.core.designsystem.component.FwNumericKeypad
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTriangleRow
import app.fuelwise.core.designsystem.component.KeypadKey
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.RepeatUnit
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Trip
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.entry.EntryDateTimePicker
import app.fuelwise.core.ui.entry.EntryLabels
import app.fuelwise.core.ui.entry.categoryName
import app.fuelwise.core.ui.entry.entryDateTimeLabel
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

@Composable
internal fun ExpenseScreen(onClose: () -> Unit, viewModel: ExpenseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onClose() } }
    // The Android photo picker: no storage permission (PRD §9).
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(EntryPhotoDraft.MAX_PHOTOS),
        viewModel::onPhotosPicked,
    )
    ExpenseContent(
        state = state,
        actions = ExpenseActions(
            onKey = viewModel::onKey,
            onFocus = viewModel::onFocus,
            onCategory = viewModel::onCategory,
            onPayment = viewModel::onPayment,
            onNotes = viewModel::onNotes,
            onPlaza = viewModel::onPlaza,
            onTollMethod = viewModel::onTollMethod,
            onDateTime = viewModel::onDateTime,
            onVehicle = viewModel::onVehicle,
            onSave = viewModel::save,
            onRepeat = viewModel::onRepeat,
            onRepeatEvery = viewModel::onRepeatEvery,
            onRepeatUnit = viewModel::onRepeatUnit,
            onTrip = viewModel::onTrip,
            onAddPhoto = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = viewModel::onRemovePhoto,
        ),
        onClose = onClose,
    )
}

internal data class ExpenseActions(
    val onKey: (KeypadKey) -> Unit = {},
    val onFocus: (ExpenseField?) -> Unit = {},
    val onCategory: (String) -> Unit = {},
    val onPayment: (PaymentMethod) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onPlaza: (String) -> Unit = {},
    val onTollMethod: (Boolean) -> Unit = {},
    val onDateTime: (Long) -> Unit = {},
    val onVehicle: (String?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRepeat: (Boolean) -> Unit = {},
    val onRepeatEvery: (String) -> Unit = {},
    val onRepeatUnit: (RepeatUnit) -> Unit = {},
    val onTrip: (String) -> Unit = {},
    val onAddPhoto: () -> Unit = {},
    val onRemovePhoto: (String) -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpenseContent(
    state: ExpenseFormState,
    actions: ExpenseActions,
    onClose: () -> Unit,
    nowUtcMs: Long = System.currentTimeMillis(),
) {
    var pickingVehicle by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val title = when {
        state.isToll && state.isEdit -> R.string.expense_title_edit_toll
        state.isToll -> R.string.expense_title_toll
        state.isEdit -> R.string.expense_title_edit
        else -> R.string.expense_title
    }
    FwEntrySheet(
        title = stringResource(title),
        onClose = onClose,
        closeDescription = stringResource(R.string.expense_close),
        headerChips = {
            state.vehicle?.let { FwHeaderChip(it.name, onClick = { if (!state.isEdit) pickingVehicle = true }) }
            FwHeaderChip(entryDateTimeLabel(state.dateTimeUtcMs, nowUtcMs, zone), onClick = {
                pickingDate = true
            }, leadingIcon = FwIcons.Schedule)
        },
        footer = {
            Column(
                Modifier.fillMaxWidth().background(
                    FwTheme.colors.surface,
                ).padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = FwTheme.colors.line)
                FwPrimaryButton(
                    stringResource(R.string.expense_save),
                    actions.onSave,
                    enabled =
                        !state.saving && state.vehicle != null,
                )
                state.focus?.let { field ->
                    FwKeypadBar(
                        focusLabel = stringResource(
                            R.string.expense_typing,
                            stringResource(
                                if (field ==
                                    ExpenseField.Amount
                                ) {
                                    R.string.expense_amount
                                } else {
                                    R.string.expense_odometer
                                },
                            ),
                        ),
                        nextLabel = if (field == ExpenseField.Amount) stringResource(R.string.expense_next) else null,
                        onNext = { actions.onFocus(if (field == ExpenseField.Amount) ExpenseField.Odometer else null) },
                    )
                    FwNumericKeypad(
                        onKey = actions.onKey,
                        backspaceDescription = stringResource(R.string.expense_backspace),
                    )
                }
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m),
        ) {
            if (state.isToll) {
                FwTextField(
                    value = state.plaza,
                    onValueChange = actions.onPlaza,
                    label = stringResource(R.string.expense_plaza),
                    placeholder = stringResource(R.string.expense_plaza_placeholder),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) actions.onFocus(null) },
                )
                val suggestions = state.plazaSuggestions.filter {
                    it != state.plaza &&
                        it.contains(state.plaza, ignoreCase = true)
                }
                if (suggestions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.take(MAX_SUGGESTIONS).forEach { FwChoiceChip(it, false, { actions.onPlaza(it) }) }
                    }
                }
                FwSegmentedControl(
                    options = listOf(stringResource(R.string.expense_fastag), stringResource(R.string.expense_cash)),
                    selectedIndex = if (state.tollByFastag) 0 else 1,
                    onSelect = { actions.onTollMethod(it == 0) },
                )
            } else {
                FwOverline(stringResource(R.string.expense_category))
                CategoryGrid(state.categories, state.categoryId, actions.onCategory)
                if (state.categoryMissing) {
                    Text(
                        stringResource(R.string.expense_error_category),
                        style = FwTheme.type.bodyS,
                        color = FwTheme.colors.negative,
                    )
                }
            }
            FwTriangleRow(
                label = stringResource(R.string.expense_amount),
                value = state.amount.takeIf { it.isNotEmpty() }?.let { "₹" + MoneyInput.groupTyped(it) }.orEmpty(),
                unit = null,
                isComputed = false,
                focused = state.focus == ExpenseField.Amount,
                onClick = { actions.onFocus(ExpenseField.Amount) },
                autoLabel = "",
            )
            if (state.amountMissing) {
                Text(
                    stringResource(R.string.expense_error_amount),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.negative,
                )
            }
            FwTriangleRow(
                label = stringResource(R.string.expense_odometer_optional),
                value = state.odometer,
                unit = stringResource(app.fuelwise.core.ui.R.string.unit_km),
                isComputed = false,
                focused = state.focus == ExpenseField.Odometer,
                onClick = { actions.onFocus(ExpenseField.Odometer) },
                autoLabel = "",
            )
            if (!state.isToll) {
                Text(stringResource(R.string.expense_payment), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(PaymentMethod.Upi, PaymentMethod.Card, PaymentMethod.Cash).forEach { m ->
                        FwChoiceChip(stringResource(EntryLabels.payment(m)), state.payment == m, {
                            actions.onPayment(m)
                        })
                    }
                }
            }
            if (state.trips.isNotEmpty()) TripLink(state, actions.onTrip, zone)
            FwTextField(
                value = state.notes,
                onValueChange = actions.onNotes,
                label = stringResource(R.string.expense_notes),
                singleLine = false,
                modifier = Modifier.onFocusChanged { if (it.isFocused) actions.onFocus(null) },
            )
            ExpensePhotos(state, actions)
            if (!state.isEdit && !state.isToll) RepeatSection(state, actions)
        }
    }
    if (pickingVehicle) {
        VehicleSwitcherSheet(
            vehicles = state.vehicles,
            selectedId = state.vehicle?.id,
            onSelect = {
                actions.onVehicle(it)
                pickingVehicle = false
            },
            onAddVehicle = { pickingVehicle = false },
            onDismiss = { pickingVehicle = false },
        )
    }
    if (pickingDate) {
        EntryDateTimePicker(state.dateTimeUtcMs, zone, {
            actions.onDateTime(it)
            pickingDate = false
        }, {
            pickingDate =
                false
        })
    }
}

/** 4-column grid of category tiles (design.md screen 10). Tolls have their own sheet, so they're left out here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(categories: List<Category>, selectedId: String?, onSelect: (String) -> Unit) {
    val shown = categories.filterNot {
        it.systemKey == SystemCategory.TollFastag ||
            it.systemKey == SystemCategory.TollCash
    }
    FlowRow(
        maxItemsInEachRow = COLUMNS,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shown.forEach { category ->
            val selected = category.id == selectedId
            Surface(
                onClick = { onSelect(category.id) },
                modifier = Modifier.weight(1f).heightIn(min = 72.dp).semantics {
                    this.selected = selected
                    role = Role.RadioButton
                },
                shape = FwTheme.radius.segment,
                color = FwTheme.colors.surface2,
                border = if (selected) BorderStroke(1.5.dp, FwTheme.colors.ink) else null,
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        painterResource(EntryLabels.categoryIcon(category)),
                        null,
                        tint = FwTheme.colors.ink,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        categoryName(category),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
        // Pad the last row so tiles keep equal widths.
        repeat((COLUMNS - shown.size % COLUMNS) % COLUMNS) { Row(Modifier.weight(1f)) {} }
    }
}

private const val COLUMNS = 4
private const val MAX_SUGGESTIONS = 4

/** "Repeat every N months" for EMIs, parking passes and the like (PRD F-04.3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatSection(state: ExpenseFormState, actions: ExpenseActions) {
    FwCheckboxRow(
        title = stringResource(R.string.expense_repeat),
        checked = state.repeat,
        onCheckedChange = actions.onRepeat,
        subtitle = stringResource(R.string.expense_repeat_body),
    )
    if (!state.repeat) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FwTextField(
            value = state.repeatEvery,
            onValueChange = actions.onRepeatEvery,
            label = stringResource(R.string.expense_repeat_every),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.width(120.dp).onFocusChanged { if (it.isFocused) actions.onFocus(null) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatUnit.entries.forEach { unit ->
                FwChoiceChip(stringResource(CareLabels.repeatUnit(unit)), state.repeatUnit == unit, {
                    actions.onRepeatUnit(unit)
                })
            }
        }
    }
}

/**
 * "Trip" (PRD F-04.1, F-10.2): link the entry to one of the vehicle's trips near its date, so it counts in that
 * trip's cost.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripLink(state: ExpenseFormState, onTrip: (String) -> Unit, zone: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.expense_trip), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.trips.forEach { trip ->
                FwChoiceChip(tripLabel(trip, zone), trip.id == state.tripId, { onTrip(trip.id) })
            }
        }
    }
}

@Composable
private fun tripLabel(trip: Trip, zone: ZoneId): String {
    val day = IndianNumberFormat.date(Instant.ofEpochMilli(trip.startUtcMs).atZone(zone).toLocalDate())
    val from = trip.fromPlace
    val to = trip.toPlace
    return if (from != null && to != null) {
        stringResource(R.string.expense_trip_places, from, to, day)
    } else {
        stringResource(R.string.expense_trip_distance, day, IndianNumberFormat.group(trip.distanceKm.roundToLong()))
    }
}

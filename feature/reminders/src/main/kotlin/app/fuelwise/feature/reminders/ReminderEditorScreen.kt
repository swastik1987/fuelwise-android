package app.fuelwise.feature.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwHeaderChip
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.entry.EntryDatePicker
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun ReminderEditorScreen(onClose: () -> Unit, viewModel: ReminderEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askPermission by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onClose() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Asked in context, right after the first reminder exists (PRD §9).
                is ReminderEditorEvent.Saved ->
                    if (event.firstReminder &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        askPermission = true
                    } else {
                        onClose()
                    }

                ReminderEditorEvent.Closed -> onClose()
            }
        }
    }
    LaunchedEffect(askPermission) {
        if (askPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    ReminderEditorContent(
        state = state,
        actions = ReminderEditorActions(
            onClose = onClose,
            onPickKind = viewModel::pickKind,
            onBackToPresets = viewModel::backToPresets,
            onVehicle = { viewModel.selectVehicle(it) },
            onTitle = viewModel::onTitle,
            onDueDate = viewModel::onDueDate,
            onDueOdometer = viewModel::onDueOdometer,
            onRepeatMonths = viewModel::onRepeatMonths,
            onRepeatKm = viewModel::onRepeatKm,
            onToggleNotify = viewModel::toggleNotify,
            onNotes = viewModel::onNotes,
            onSave = viewModel::save,
            onDone = viewModel::markDone,
            onDelete = viewModel::delete,
        ),
    )
}

internal data class ReminderEditorActions(
    val onClose: () -> Unit = {},
    val onPickKind: (ReminderKind) -> Unit = {},
    val onBackToPresets: () -> Unit = {},
    val onVehicle: (String) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onDueDate: (Long?) -> Unit = {},
    val onDueOdometer: (String) -> Unit = {},
    val onRepeatMonths: (String) -> Unit = {},
    val onRepeatKm: (String) -> Unit = {},
    val onToggleNotify: (Int) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** Add reminder (design.md screen 23): preset grid → by date / by km / both → repeat → notify offsets. */
@Composable
internal fun ReminderEditorContent(state: ReminderForm, actions: ReminderEditorActions) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(if (state.isEdit) R.string.editor_edit_title else R.string.editor_add_title),
            onBack = if (state.kind != null && !state.isEdit) actions.onBackToPresets else actions.onClose,
            backContentDescription = stringResource(R.string.reminders_back),
        )
        if (state.loading) return@Column
        val kind = state.kind
        if (kind == null) PresetGrid(actions.onPickKind) else Form(state, kind, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetGrid(onPick: (ReminderKind) -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
    ) {
        FwOverline(stringResource(R.string.editor_presets))
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = PRESET_COLUMNS,
        ) {
            PRESETS.forEach { kind ->
                Column(
                    Modifier.weight(1f).heightIn(min = 88.dp).background(FwTheme.colors.surface, FwTheme.radius.card)
                        .clickable(role = Role.Button) { onPick(kind) }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        painterResource(CareLabels.reminderIcon(kind)),
                        null,
                        tint = FwTheme.colors.ink,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        stringResource(CareLabels.reminder(kind)),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Form(state: ReminderForm, kind: ReminderKind, actions: ReminderEditorActions) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var switcher by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    Column(
        Modifier.verticalScroll(
            rememberScrollState(),
        ).padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.l),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(CareLabels.reminderIcon(kind)),
                null,
                tint = FwTheme.colors.ink,
                modifier = Modifier.size(24.dp),
            )
            Text(
                stringResource(CareLabels.reminder(kind)),
                style = FwTheme.type.h3,
                color = FwTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            state.vehicle?.let { v ->
                FwHeaderChip(v.name, onClick = {
                    if (state.vehicles.size >
                        1
                    ) {
                        switcher = true
                    }
                }, leadingIcon = FwIcons.Car)
            }
        }
        if (kind == ReminderKind.Puc) {
            Text(stringResource(R.string.editor_puc_helper), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
        }
        FwTextField(
            value = state.title,
            onValueChange = actions.onTitle,
            label = stringResource(
                if (kind ==
                    ReminderKind.Custom
                ) {
                    R.string.editor_title_required
                } else {
                    R.string.editor_title_optional
                },
            ),
            error = if (state.missingTitle) stringResource(R.string.editor_title_missing) else null,
        )
        FwOverline(stringResource(R.string.editor_due))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.dueDateUtcMs?.let { IndianNumberFormat.date(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }
                    ?: stringResource(R.string.editor_no_date),
                style = FwTheme.type.body,
                color = FwTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            FwTextButton(stringResource(R.string.editor_pick_date), { pickingDate = true })
            if (state.dueDateUtcMs !=
                null
            ) {
                FwTextButton(stringResource(R.string.editor_clear), { actions.onDueDate(null) })
            }
        }
        FwTextField(
            value = state.dueOdometer,
            onValueChange = actions.onDueOdometer,
            label = stringResource(R.string.editor_due_km),
            unit = "km",
            placeholder = state.currentOdometerKm?.let {
                stringResource(R.string.editor_now_at, IndianNumberFormat.decimal(it, 0))
            },
            keyboardType = KeyboardType.Number,
        )
        if (state.missingDue) {
            Text(
                stringResource(R.string.editor_due_missing),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.negative,
            )
        }
        FwOverline(stringResource(R.string.editor_repeat))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FwTextField(
                value = state.repeatMonths,
                onValueChange = actions.onRepeatMonths,
                label = stringResource(R.string.editor_every_months),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            FwTextField(
                value = state.repeatKm,
                onValueChange = actions.onRepeatKm,
                label = stringResource(R.string.editor_every_km),
                unit = "km",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        FwOverline(stringResource(R.string.editor_notify))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NOTIFY_OPTIONS.forEach { days ->
                FwFilterChip(
                    pluralStringResource(R.plurals.editor_days_before, days, days),
                    days in state.notifyDaysBefore,
                    { actions.onToggleNotify(days) },
                )
            }
        }
        FwTextField(
            value = state.notes,
            onValueChange = actions.onNotes,
            label = stringResource(R.string.editor_notes),
            singleLine = false,
        )
        FwInkButton(stringResource(R.string.editor_save), actions.onSave, Modifier.fillMaxWidth())
        if (state.isEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwSecondaryButton(
                    stringResource(R.string.editor_mark_done),
                    actions.onDone,
                    Modifier.weight(1f),
                    leadingIcon = FwIcons.Check,
                )
                FwSecondaryButton(
                    stringResource(R.string.editor_delete),
                    actions.onDelete,
                    Modifier.weight(1f),
                    leadingIcon = FwIcons.Delete,
                )
            }
        }
        Box(Modifier.width(1.dp).heightIn(min = 24.dp))
    }
    if (pickingDate) {
        EntryDatePicker(
            initialUtcMs = state.dueDateUtcMs ?: state.nowUtcMs,
            zone = zone,
            onPicked = {
                actions.onDueDate(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
    if (switcher) {
        VehicleSwitcherSheet(
            vehicles = state.vehicles,
            selectedId = state.vehicle?.id,
            onSelect = { id ->
                id?.let(actions.onVehicle)
                switcher = false
            },
            onAddVehicle = { switcher = false },
            onDismiss = { switcher = false },
        )
    }
}

private const val PRESET_COLUMNS = 3
private val NOTIFY_OPTIONS = listOf(30, 15, 7, 3, 1)
private val PRESETS = listOf(
    ReminderKind.Insurance,
    ReminderKind.Puc,
    ReminderKind.Service,
    ReminderKind.EngineOil,
    ReminderKind.Tyres,
    ReminderKind.Battery,
    ReminderKind.Rc,
    ReminderKind.Licence,
    ReminderKind.Warranty,
    ReminderKind.Coolant,
    ReminderKind.Wipers,
    ReminderKind.Fitness,
    ReminderKind.Custom,
)

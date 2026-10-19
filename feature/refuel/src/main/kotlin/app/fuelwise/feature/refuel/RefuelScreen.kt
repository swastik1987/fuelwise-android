package app.fuelwise.feature.refuel

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCheckboxRow
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwEntrySheet
import app.fuelwise.core.designsystem.component.FwHeaderChip
import app.fuelwise.core.designsystem.component.FwKeypadBar
import app.fuelwise.core.designsystem.component.FwNumericKeypad
import app.fuelwise.core.designsystem.component.FwOdometerField
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTriangleRow
import app.fuelwise.core.designsystem.component.KeypadKey
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.entry.CheckFillUp
import app.fuelwise.core.domain.entry.PriceTriangle.Field
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.PriceSource
import app.fuelwise.core.ui.entry.EntryDateTimePicker
import app.fuelwise.core.ui.entry.EntryLabels
import app.fuelwise.core.ui.entry.entryDateTimeLabel
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
internal fun RefuelScreen(
    onClose: () -> Unit,
    onScan: (vehicleId: String?) -> Unit = {},
    viewModel: RefuelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefuelContent(state, viewModel.actions().copy(onScan = { onScan(state.vehicle?.id) }), onClose)
}

/** Callbacks for [RefuelContent]; defaults make previews and screenshot tests easy. */
internal data class RefuelActions(
    val onKey: (KeypadKey) -> Unit = {},
    val onFocus: (NumericField?) -> Unit = {},
    val onNext: () -> Unit = {},
    val onUsePrediction: () -> Unit = {},
    val onFull: (Boolean) -> Unit = {},
    val onToggleMore: () -> Unit = {},
    val onGrade: (FuelGrade) -> Unit = {},
    val onPayment: (PaymentMethod) -> Unit = {},
    val onStation: (String) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onOperator: (String) -> Unit = {},
    val onMissedPrevious: (Boolean) -> Unit = {},
    val onSoc: (Float, Float) -> Unit = { _, _ -> },
    val onCharger: (ChargerType) -> Unit = {},
    val onDateTime: (Long) -> Unit = {},
    val onTank: (Int) -> Unit = {},
    val onVehicle: (String?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onRemoveScanPhoto: () -> Unit = {},
)

private fun RefuelViewModel.actions() = RefuelActions(
    onKey = ::onKey,
    onFocus = ::onFocus,
    onNext = ::onNextField,
    onUsePrediction = ::onUsePrediction,
    onFull = ::onFullChange,
    onToggleMore = ::onToggleMore,
    onGrade = ::onGrade,
    onPayment = ::onPayment,
    onStation = ::onStation,
    onNotes = ::onNotes,
    onOperator = ::onOperator,
    onMissedPrevious = ::onMissedPrevious,
    onSoc = ::onSoc,
    onCharger = ::onCharger,
    onDateTime = ::onDateTime,
    onTank = ::onTank,
    onVehicle = ::onVehicle,
    onSave = ::save,
    onRemoveScanPhoto = ::onRemoveScanPhoto,
)

@Composable
internal fun RefuelContent(
    state: RefuelFormState,
    actions: RefuelActions,
    onClose: () -> Unit,
    nowUtcMs: Long = System.currentTimeMillis(),
) {
    var pickingVehicle by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val title = when {
        state.isEdit && state.isCharge -> R.string.refuel_title_edit_charge
        state.isEdit -> R.string.refuel_title_edit_fuel
        state.isCharge -> R.string.refuel_title_charge
        else -> R.string.refuel_title_fuel
    }
    FwEntrySheet(
        title = stringResource(title),
        onClose = onClose,
        closeDescription = stringResource(R.string.refuel_close),
        headerChips = {
            state.vehicle?.let { v -> FwHeaderChip(v.name, onClick = { if (!state.isEdit) pickingVehicle = true }) }
            FwHeaderChip(
                entryDateTimeLabel(state.dateTimeUtcMs, nowUtcMs, zone),
                onClick = { pickingDate = true },
                leadingIcon = FwIcons.Schedule,
            )
        },
        footer = { Footer(state, actions) },
    ) {
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
        ) {
            val vehicle = state.vehicle
            if (vehicle != null && vehicle.isBiFuel && !state.isEdit) {
                FwSegmentedControl(
                    options = vehicle.tanks.map {
                        stringResource(VehicleLabels.energy(it.energyType)) + " · " +
                            stringResource(VehicleLabels.unit(it.energyType.canonicalUnit))
                    },
                    selectedIndex = state.tankIndex,
                    onSelect = actions.onTank,
                )
            }
            if (!state.isEdit && !state.isCharge && state.vehicle != null) ScanBanner(actions.onScan)
            OdometerSection(state, actions)
            TriangleSection(state, actions)
            if (state.scanPhotoToken != null) ScanPhotoRow(actions.onRemoveScanPhoto)
            state.warnings.forEach { warning ->
                if (warning is CheckFillUp.Warning.MoreThanTank) {
                    Banner(
                        stringResource(
                            R.string.refuel_warning_tank,
                            IndianNumberFormat.decimal(warning.capacity, 0),
                            unitLabel(state),
                        ),
                    )
                }
            }
            if (state.isCharge) ChargeSection(state, actions) else FuelSection(state, actions)
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
        EntryDateTimePicker(
            initialUtcMs = state.dateTimeUtcMs,
            zone = zone,
            onPicked = {
                actions.onDateTime(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
    state.reward?.let { reward ->
        RewardOverlay(
            reward = reward,
            vehicleName = state.vehicle?.name.orEmpty(),
            unit = unitLabel(state),
            onDismiss = onClose,
        )
    }
}

@Composable
private fun OdometerSection(state: RefuelFormState, actions: RefuelActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FwOverline(stringResource(R.string.refuel_odometer))
        val predicted = state.predictedOdometer?.toLong()?.toString()
        FwOdometerField(
            typed = state.odometer.substringBefore('.'),
            predicted = predicted,
            focused = state.focus == NumericField.Odometer,
            error = state.problems.isNotEmpty() || RequiredField.Odometer in state.missing,
            onClick = { actions.onFocus(NumericField.Odometer) },
            contentDescription = stringResource(R.string.refuel_odometer_cd, state.odometer.ifEmpty { "—" }),
            trailing = {
                if (state.odometer.isEmpty() && state.predictedOdometer != null) {
                    Surface(
                        onClick = actions.onUsePrediction,
                        shape = FwTheme.radius.pill,
                        color = FwTheme.colors.accentSoft,
                    ) {
                        Text(
                            stringResource(
                                R.string.refuel_use_prediction,
                                IndianNumberFormat.decimal(state.predictedOdometer, 0),
                            ),
                            style = FwTheme.type.label,
                            color = FwTheme.colors.accent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            },
        )
        state.problems.forEach { problem ->
            val (text, entry) = when (problem) {
                is CheckFillUp.Problem.OdometerNotAbovePrevious -> R.string.refuel_error_odo_low to problem.previous
                is CheckFillUp.Problem.OdometerNotBelowNext -> R.string.refuel_error_odo_high to problem.next
            }
            Text(
                stringResource(
                    text,
                    IndianNumberFormat.decimal(entry.odometerKm, 0),
                    IndianNumberFormat.date(localDate(entry.dateTimeUtcMs)),
                ),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.negative,
            )
        }
        if (RequiredField.Odometer in state.missing) {
            Text(
                stringResource(R.string.refuel_error_odo_missing),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.negative,
            )
        }
    }
}

@Composable
private fun TriangleSection(state: RefuelFormState, actions: RefuelActions) {
    val unit = unitLabel(state)
    val auto = stringResource(R.string.refuel_auto)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FwTriangleRow(
            label = stringResource(if (state.isCharge) R.string.refuel_energy else R.string.refuel_quantity),
            value = state.quantity,
            unit = unit,
            isComputed = state.computedField == Field.Quantity,
            focused = state.focus == NumericField.Quantity,
            onClick = { actions.onFocus(NumericField.Quantity) },
            autoLabel = auto,
        )
        FwTriangleRow(
            label = stringResource(if (state.isCharge) R.string.refuel_tariff else R.string.refuel_price, unit),
            value = state.unitPrice.takeIf { it.isNotEmpty() }?.let { "₹" + MoneyInput.groupTyped(it) }.orEmpty(),
            unit = null,
            isComputed = state.computedField == Field.UnitPrice,
            focused = state.focus == NumericField.UnitPrice,
            onClick = { actions.onFocus(NumericField.UnitPrice) },
            autoLabel = auto,
            caption = when {
                state.computedField == Field.UnitPrice -> null
                state.homeTariffPrice -> stringResource(R.string.refuel_price_home_tariff)
                state.priceSource == PriceSource.LastPaid -> stringResource(R.string.refuel_price_last_paid)
                else -> null
            },
        )
        FwTriangleRow(
            label = stringResource(if (state.isCharge) R.string.refuel_cost else R.string.refuel_total),
            value = state.total.takeIf { it.isNotEmpty() }?.let { "₹" + MoneyInput.groupTyped(it) }.orEmpty(),
            unit = null,
            isComputed = state.computedField == Field.Total,
            focused = state.focus == NumericField.Total,
            onClick = { actions.onFocus(NumericField.Total) },
            autoLabel = auto,
        )
        if (RequiredField.Quantity in state.missing || RequiredField.Total in state.missing) {
            Text(
                stringResource(R.string.refuel_error_amounts),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.negative,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelSection(state: RefuelFormState, actions: RefuelActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FwSegmentedControl(
            options = listOf(stringResource(R.string.refuel_full), stringResource(R.string.refuel_partial)),
            selectedIndex = if (state.isFull) 0 else 1,
            onSelect = { actions.onFull(it == 0) },
            modifier = Modifier.weight(1f),
        )
        MoreToggle(state.moreExpanded, actions.onToggleMore)
    }
    if (state.moreExpanded) {
        HorizontalDivider(color = FwTheme.colors.line, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.refuel_grade), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelGrade.entries.forEach { g ->
                FwChoiceChip(stringResource(EntryLabels.grade(g)), state.grade == g, { actions.onGrade(g) })
            }
        }
        PaymentChips(state.payment, actions.onPayment)
        FwTriangleRow(
            label = stringResource(R.string.refuel_discount),
            value = state.discount.takeIf { it.isNotEmpty() }?.let { "₹" + MoneyInput.groupTyped(it) }.orEmpty(),
            unit = null,
            isComputed = false,
            focused = state.focus == NumericField.Discount,
            onClick = { actions.onFocus(NumericField.Discount) },
            autoLabel = "",
        )
        FwTextField(
            value = state.station,
            onValueChange = actions.onStation,
            label = stringResource(R.string.refuel_station),
            placeholder = stringResource(R.string.refuel_station_placeholder),
            modifier = Modifier.onFocusChanged { if (it.isFocused) actions.onFocus(null) },
        )
        FwTextField(
            value = state.notes,
            onValueChange = actions.onNotes,
            label = stringResource(R.string.refuel_notes),
            singleLine = false,
            modifier = Modifier.onFocusChanged { if (it.isFocused) actions.onFocus(null) },
        )
        FwCheckboxRow(
            title = stringResource(R.string.refuel_missed),
            subtitle = stringResource(R.string.refuel_missed_hint),
            checked = state.missedPrevious,
            onCheckedChange = actions.onMissedPrevious,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChargeSection(state: RefuelFormState, actions: RefuelActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            FwOverline(stringResource(R.string.refuel_battery), Modifier.weight(1f))
            Text(
                stringResource(R.string.refuel_soc_range, state.socStart.roundToInt(), state.socEnd.roundToInt()),
                style = FwTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                color = FwTheme.colors.ink,
            )
        }
        RangeSlider(
            value = state.socStart..state.socEnd,
            onValueChange = { actions.onSoc(it.start.roundToInt().toFloat(), it.endInclusive.roundToInt().toFloat()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = FwTheme.colors.surface,
                activeTrackColor = app.fuelwise.core.designsystem.theme.EnergyColors.Electric,
                inactiveTrackColor = FwTheme.colors.surface2,
                activeTickColor = app.fuelwise.core.designsystem.theme.EnergyColors.Electric,
                inactiveTickColor = FwTheme.colors.surface2,
            ),
        )
        FwOverline(stringResource(R.string.refuel_charger))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChargerType.entries.forEach { c ->
                FwChoiceChip(stringResource(EntryLabels.charger(c)), state.charger == c, { actions.onCharger(c) })
            }
        }
        FwTextField(
            value = state.operator,
            onValueChange = actions.onOperator,
            label = stringResource(R.string.refuel_operator),
            modifier = Modifier.onFocusChanged { if (it.isFocused) actions.onFocus(null) },
        )
        FwTriangleRow(
            label = stringResource(R.string.refuel_duration),
            value = state.durationMin,
            unit = stringResource(R.string.refuel_minutes),
            isComputed = false,
            focused = state.focus == NumericField.Duration,
            onClick = { actions.onFocus(NumericField.Duration) },
            autoLabel = "",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentChips(selected: PaymentMethod?, onSelect: (PaymentMethod) -> Unit) {
    Text(stringResource(R.string.refuel_payment), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PaymentMethod.Upi, PaymentMethod.Card, PaymentMethod.Cash, PaymentMethod.FleetCard).forEach { m ->
            FwChoiceChip(stringResource(EntryLabels.payment(m)), selected == m, { onSelect(m) })
        }
    }
}

@Composable
private fun MoreToggle(expanded: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = FwTheme.colors.surface, shape = FwTheme.radius.pill) {
        Row(
            Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.refuel_more), style = FwTheme.type.label, color = FwTheme.colors.ink)
            Icon(
                painterResource(FwIcons.ExpandMore),
                contentDescription = null,
                tint = FwTheme.colors.ink,
                modifier = Modifier.size(20.dp).rotate(if (expanded) EXPANDED_ROTATION else 0f),
            )
        }
    }
}

@Composable
private fun Banner(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FwTheme.colors.warning.copy(alpha = BANNER_ALPHA), FwTheme.radius.input)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(FwIcons.Info), null, tint = FwTheme.colors.ink, modifier = Modifier.size(20.dp))
        Text(text, style = FwTheme.type.bodyS, color = FwTheme.colors.ink)
    }
}

@Composable
private fun Footer(state: RefuelFormState, actions: RefuelActions) {
    Column(
        Modifier.fillMaxWidth().background(
            FwTheme.colors.surface,
        ).padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = FwTheme.colors.line)
        FwPrimaryButton(
            text = stringResource(R.string.refuel_save),
            onClick = actions.onSave,
            enabled = !state.saving && !state.loading && state.vehicle != null,
        )
        state.focus?.let { field ->
            FwKeypadBar(
                focusLabel = stringResource(R.string.refuel_typing, focusName(field, state)),
                nextLabel = stringResource(R.string.refuel_next_field),
                onNext = actions.onNext,
            )
            FwNumericKeypad(
                onKey = actions.onKey,
                allowDot = field != NumericField.Duration,
                backspaceDescription = stringResource(R.string.refuel_backspace),
            )
        }
    }
}

@Composable
private fun focusName(field: NumericField, state: RefuelFormState): String = when (field) {
    NumericField.Odometer -> stringResource(R.string.refuel_odometer)

    NumericField.Quantity -> stringResource(if (state.isCharge) R.string.refuel_energy else R.string.refuel_quantity) +
        " (" +
        unitLabel(state) +
        ")"

    NumericField.UnitPrice -> stringResource(
        if (state.isCharge) R.string.refuel_tariff else R.string.refuel_price,
        unitLabel(state),
    )

    NumericField.Total -> stringResource(if (state.isCharge) R.string.refuel_cost else R.string.refuel_total)

    NumericField.Discount -> stringResource(R.string.refuel_discount)

    NumericField.Duration -> stringResource(R.string.refuel_duration)
}

@Composable
internal fun unitLabel(state: RefuelFormState): String =
    state.tank?.let { stringResource(VehicleLabels.unit(it.energyType.canonicalUnit)) }.orEmpty()

internal fun localDate(utcMs: Long) = java.time.Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault()).toLocalDate()

private const val EXPANDED_ROTATION = 180f
private const val BANNER_ALPHA = 0.16f

/** Slim "Scan pump meter" banner at the top of Add fuel (design.md screen 6 → screen 9, PRD F-15). */
@Composable
private fun ScanBanner(onScan: () -> Unit) {
    Surface(
        onClick = onScan,
        shape = FwTheme.radius.input,
        color = FwTheme.colors.surface2,
        modifier = Modifier.fillMaxWidth().heightIn(min = FwTheme.size.minTouchTarget),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(FwIcons.Camera), null, tint = FwTheme.colors.ink, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.refuel_scan_banner),
                style = FwTheme.type.label,
                color = FwTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(FwIcons.ChevronRight),
                null,
                tint = FwTheme.colors.ink2,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The scan photo that will be attached on save, with a way to leave it off. */
@Composable
private fun ScanPhotoRow(onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = FwTheme.size.minTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(FwIcons.Camera), null, tint = FwTheme.colors.ink2, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.refuel_scan_photo),
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        FwTextButton(stringResource(R.string.refuel_scan_photo_remove), onRemove)
    }
}

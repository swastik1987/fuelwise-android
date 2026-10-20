package app.fuelwise.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwEntrySheet
import app.fuelwise.core.designsystem.component.FwHeaderChip
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.TripCostBasis
import app.fuelwise.core.domain.trip.TripCostEstimate
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.ui.entry.EntryDateTimePicker
import app.fuelwise.core.ui.entry.entryDateTimeLabel
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.efficiencyText
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.ZoneId

internal data class TripEditorActions(
    val onVehicle: (String?) -> Unit = {},
    val onDateTime: (Long) -> Unit = {},
    val onStartOdometer: (String) -> Unit = {},
    val onEndOdometer: (String) -> Unit = {},
    val onDistance: (String) -> Unit = {},
    val onPurpose: (TripPurpose) -> Unit = {},
    val onCustomPurpose: (String) -> Unit = {},
    val onFrom: (String) -> Unit = {},
    val onTo: (String) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
internal fun TripEditorScreen(onClose: () -> Unit, viewModel: TripEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onClose() } }
    TripEditorContent(
        state,
        TripEditorActions(
            onVehicle = viewModel::onVehicle,
            onDateTime = viewModel::onDateTime,
            onStartOdometer = viewModel::onStartOdometer,
            onEndOdometer = viewModel::onEndOdometer,
            onDistance = viewModel::onDistance,
            onPurpose = viewModel::onPurpose,
            onCustomPurpose = viewModel::onCustomPurpose,
            onFrom = viewModel::onFrom,
            onTo = viewModel::onTo,
            onNotes = viewModel::onNotes,
            onSave = viewModel::save,
        ),
        onClose,
    )
}

/** Add / edit a manual trip (PRD F-07.1): odometers or a distance, date, purpose, places and a note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TripEditorContent(
    state: TripFormState,
    actions: TripEditorActions,
    onClose: () -> Unit,
    nowUtcMs: Long = System.currentTimeMillis(),
) {
    var pickingVehicle by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    FwEntrySheet(
        title = stringResource(if (state.isEdit) R.string.trips_edit_title else R.string.trips_add_title),
        onClose = onClose,
        closeDescription = stringResource(R.string.trips_close),
        headerChips = {
            state.vehicle?.let {
                FwHeaderChip(it.name, onClick = { if (state.vehicles.size > 1) pickingVehicle = true })
            }
            FwHeaderChip(
                entryDateTimeLabel(state.startUtcMs, nowUtcMs, zone),
                onClick = { pickingDate = true },
                leadingIcon = FwIcons.Schedule,
            )
        },
        footer = {
            Column(
                Modifier.fillMaxWidth().background(FwTheme.colors.surface)
                    .padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = FwTheme.colors.line)
                FwPrimaryButton(
                    stringResource(R.string.trips_save),
                    actions.onSave,
                    enabled = !state.saving && state.vehicle != null,
                )
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.gutter, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwTextField(
                    value = state.startOdometer,
                    onValueChange = actions.onStartOdometer,
                    label = stringResource(R.string.trips_start_odometer),
                    unit = stringResource(R.string.trips_km_unit),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                FwTextField(
                    value = state.endOdometer,
                    onValueChange = actions.onEndOdometer,
                    label = stringResource(R.string.trips_end_odometer),
                    unit = stringResource(R.string.trips_km_unit),
                    keyboardType = KeyboardType.Decimal,
                    error = if (state.distanceError == DistanceError.EndNotAfterStart) {
                        stringResource(R.string.trips_error_end_before_start)
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            val fromOdometers = state.odometerDistanceKm
            if (fromOdometers != null) {
                Text(
                    stringResource(R.string.trips_distance_from_odometer, formatKm(fromOdometers)),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
            } else {
                FwTextField(
                    value = state.distance,
                    onValueChange = actions.onDistance,
                    label = stringResource(R.string.trips_distance),
                    placeholder = stringResource(R.string.trips_distance_hint),
                    unit = stringResource(R.string.trips_km_unit),
                    keyboardType = KeyboardType.Decimal,
                    error = if (state.distanceError == DistanceError.Missing) {
                        stringResource(R.string.trips_error_distance)
                    } else {
                        null
                    },
                )
            }
            EstimateLine(state.estimate, state.unitSettings.forVehicle(state.vehicle?.id))

            FwOverline(stringResource(R.string.trips_purpose))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TripPurpose.entries.forEach { p ->
                    FwChoiceChip(stringResource(TripLabels.purpose(p)), state.purpose == p, { actions.onPurpose(p) })
                }
            }
            if (state.purpose == TripPurpose.Custom) {
                FwTextField(
                    value = state.customPurpose,
                    onValueChange = actions.onCustomPurpose,
                    label = stringResource(R.string.trips_custom_purpose),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwTextField(
                    value = state.fromPlace,
                    onValueChange = actions.onFrom,
                    label = stringResource(R.string.trips_from),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.weight(1f),
                )
                FwTextField(
                    value = state.toPlace,
                    onValueChange = actions.onTo,
                    label = stringResource(R.string.trips_to),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.weight(1f),
                )
            }
            FwTextField(
                value = state.notes,
                onValueChange = actions.onNotes,
                label = stringResource(R.string.trips_notes),
                singleLine = false,
                imeAction = ImeAction.Default,
            )
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
        EntryDateTimePicker(state.startUtcMs, zone, {
            actions.onDateTime(it)
            pickingDate = false
        }, { pickingDate = false })
    }
}

/** "Fuel ≈ ₹896 est. · 10.9 L at 13.4 km/L", or why there's no estimate yet. */
@Composable
private fun EstimateLine(estimate: TripCostEstimate?, units: DisplayUnits) {
    val text = when (val basis = estimate?.basis) {
        null -> stringResource(R.string.trips_estimate_none)

        is TripCostBasis.Efficiency -> {
            val unit = stringResource(VehicleLabels.unit(basis.energyType.canonicalUnit))
            stringResource(
                R.string.trips_estimate_efficiency,
                IndianNumberFormat.rupeesWhole(estimate.fuelCost),
                IndianNumberFormat.decimal(basis.quantity, 1),
                unit,
                efficiencyText(basis.kmPerUnit, basis.energyType.canonicalUnit, units),
            )
        }

        is TripCostBasis.CostPerKm -> stringResource(
            R.string.trips_estimate_per_km,
            IndianNumberFormat.rupeesWhole(estimate.fuelCost),
            IndianNumberFormat.rupees(basis.costPerKm),
        )
    }
    Text(text, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
}

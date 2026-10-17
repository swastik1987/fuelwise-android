package app.fuelwise.feature.garage.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.vehicle.VehicleDraftError
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.VehicleType
import app.fuelwise.core.ui.format.NumberInput
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.feature.garage.R

@Composable
internal fun VehicleEditorScreen(
    onBack: () -> Unit,
    onSaved: (vehicleId: String, onboarding: Boolean) -> Unit,
    viewModel: VehicleEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VehicleEditorEvent.Saved -> onSaved(event.vehicleId, event.onboarding)
            }
        }
    }
    VehicleEditorContent(
        state = state,
        onBack = onBack,
        actions = VehicleEditorActions(
            onName = viewModel::onNameChange,
            onType = viewModel::onTypeChange,
            onFuel = viewModel::onFuelSetupChange,
            onOdometer = viewModel::onOdometerChange,
            onCapacity = viewModel::onCapacityChange,
            onRegistration = viewModel::onRegistrationChange,
            onEmissionNorm = viewModel::onEmissionNormChange,
            onClaimed = viewModel::onClaimedChange,
            onToggleMore = viewModel::onToggleMore,
            onSave = viewModel::save,
        ),
    )
}

internal data class VehicleEditorActions(
    val onName: (String) -> Unit = {},
    val onType: (VehicleType) -> Unit = {},
    val onFuel: (FuelSetup) -> Unit = {},
    val onOdometer: (String) -> Unit = {},
    val onCapacity: (Int, String) -> Unit = { _, _ -> },
    val onRegistration: (String) -> Unit = {},
    val onEmissionNorm: (EmissionNorm) -> Unit = {},
    val onClaimed: (String) -> Unit = {},
    val onToggleMore: () -> Unit = {},
    val onSave: () -> Unit = {},
)

/** Add your first vehicle / Add vehicle / Edit vehicle (design.md screen 2). A single scrolling form, not a wizard. */
@Composable
internal fun VehicleEditorContent(state: VehicleFormState, onBack: () -> Unit, actions: VehicleEditorActions) {
    val title = when {
        state.isEditing -> R.string.editor_title_edit
        state.onboarding -> R.string.editor_title_first
        else -> R.string.editor_title_add
    }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg).imePadding()) {
        FwTopBar(stringResource(title), onBack = onBack, backContentDescription = stringResource(R.string.editor_back))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
        ) {
            TypePicker(state.type, actions.onType)
            FuelPicker(state, actions.onFuel)
            FwTextField(
                value = state.name,
                onValueChange = actions.onName,
                label = stringResource(R.string.editor_name),
                placeholder = stringResource(R.string.editor_name_placeholder),
                error = state.errors.messageFor(VehicleDraftError.NameRequired, VehicleDraftError.NameTooLong),
                capitalization = KeyboardCapitalization.Words,
            )
            FwTextField(
                value = state.odometer,
                onValueChange = actions.onOdometer,
                label = stringResource(
                    if (state.isEditing) R.string.editor_start_odometer else R.string.editor_odometer,
                ),
                unit = stringResource(app.fuelwise.core.ui.R.string.unit_km),
                error = state.errors.messageFor(VehicleDraftError.OdometerRequired, VehicleDraftError.OdometerNegative),
                keyboardType = KeyboardType.Decimal,
                textStyle = FwTheme.type.mono,
            )
            MoreDetails(state, actions)
        }
        Box(Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s)) {
            FwPrimaryButton(
                text = stringResource(if (state.isEditing) R.string.editor_save else R.string.editor_add),
                onClick = actions.onSave,
                enabled = !state.isSaving && !state.isLoading,
            )
        }
    }
}

@Composable
private fun TypePicker(selected: VehicleType, onSelect: (VehicleType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s)) {
        FwOverline(stringResource(R.string.editor_type))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
        ) {
            PICKER_TYPES.forEach { type -> TypeTile(type, type == selected) { onSelect(type) } }
        }
    }
}

@Composable
private fun TypeTile(type: VehicleType, selected: Boolean, onClick: () -> Unit) {
    val colors = FwTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.width(76.dp).heightIn(min = 84.dp).semantics {
            this.selected = selected
            role = Role.RadioButton
        },
        shape = FwTheme.radius.input,
        color = if (selected) colors.surface else colors.surface2,
        contentColor = if (selected) colors.ink else colors.ink2,
        border = if (selected) BorderStroke(1.5.dp, colors.ink) else null,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(VehicleLabels.typeIcon(type)),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(stringResource(VehicleLabels.type(type)), style = FwTheme.type.caption)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelPicker(state: VehicleFormState, onSelect: (FuelSetup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s)) {
        FwOverline(stringResource(R.string.editor_fuel))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelSetup.entries.forEach { setup ->
                // In edit mode only the current setup is shown: fuel can't change once entries may exist.
                if (!state.isEditing || setup == state.fuelSetup) {
                    FwFilterChip(
                        label = stringResource(VehicleLabels.fuelSetup(setup)),
                        selected = setup == state.fuelSetup,
                        onClick = { onSelect(setup) },
                    )
                }
            }
        }
        if (state.isEditing) {
            Text(stringResource(R.string.editor_fuel_locked), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreDetails(state: VehicleFormState, actions: VehicleEditorActions) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m)) {
        HorizontalDivider(color = FwTheme.colors.line)
        Surface(onClick = actions.onToggleMore, color = FwTheme.colors.bg) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_more), style = FwTheme.type.label, color = FwTheme.colors.ink)
                    Text(
                        stringResource(R.string.editor_more_hint),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink2,
                    )
                }
                Icon(
                    painterResource(FwIcons.ExpandMore),
                    contentDescription = null,
                    tint = FwTheme.colors.ink2,
                    modifier = Modifier.rotate(if (state.moreExpanded) EXPANDED_ROTATION else 0f),
                )
            }
        }
        if (state.moreExpanded) {
            state.fuelSetup.tanks.forEachIndexed { index, energy ->
                val unit = stringResource(VehicleLabels.unit(energy.canonicalUnit))
                FwTextField(
                    value = state.capacities.getOrElse(index) { "" },
                    onValueChange = { actions.onCapacity(index, it) },
                    label = stringResource(
                        if (state.fuelSetup == FuelSetup.Electric) R.string.editor_battery else R.string.editor_tank,
                        stringResource(VehicleLabels.energy(energy)),
                    ),
                    unit = unit,
                    keyboardType = KeyboardType.Decimal,
                    error = if (VehicleDraftError.CapacityNotPositive in state.errors && state.capacityInvalid(index)) {
                        stringResource(R.string.editor_error_capacity)
                    } else {
                        null
                    },
                )
            }
            if (state.fuelSetup.isBiFuel) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(
                            8.dp,
                        ).background(VehicleLabels.energyColor(state.fuelSetup.tanks.first()), CircleShape),
                    )
                    Text(
                        stringResource(R.string.editor_bifuel_hint),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink2,
                    )
                }
            }
            FwTextField(
                value = state.registration,
                onValueChange = actions.onRegistration,
                label = stringResource(R.string.editor_registration),
                placeholder = stringResource(R.string.editor_registration_placeholder),
                error = state.errors.messageFor(VehicleDraftError.RegistrationInvalid),
                capitalization = KeyboardCapitalization.Characters,
                textStyle = FwTheme.type.body.copy(fontFamily = FwTheme.type.mono.fontFamily),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.editor_emission_norm),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmissionNorm.entries.forEach { norm ->
                        FwChoiceChip(stringResource(normLabel(norm)), norm == state.emissionNorm, {
                            actions.onEmissionNorm(norm)
                        })
                    }
                }
            }
            FwTextField(
                value = state.claimed,
                onValueChange = actions.onClaimed,
                label = stringResource(R.string.editor_claimed),
                unit = stringResource(
                    R.string.editor_claimed_unit,
                    stringResource(VehicleLabels.unit(state.fuelSetup.tanks.first().canonicalUnit)),
                ),
                error = state.errors.messageFor(VehicleDraftError.ClaimedNotPositive),
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            )
        }
    }
}

private fun VehicleFormState.capacityInvalid(index: Int): Boolean {
    val text = capacities.getOrNull(index).orEmpty()
    return text.isNotEmpty() && (NumberInput.parseDecimal(text) ?: 0.0) <= 0.0
}

@Composable
private fun Set<VehicleDraftError>.messageFor(vararg candidates: VehicleDraftError): String? =
    candidates.firstOrNull { it in this }?.let { stringResource(errorMessage(it)) }

private fun errorMessage(error: VehicleDraftError): Int = when (error) {
    VehicleDraftError.NameRequired -> R.string.editor_error_name
    VehicleDraftError.NameTooLong -> R.string.editor_error_name_long
    VehicleDraftError.OdometerRequired -> R.string.editor_error_odometer
    VehicleDraftError.OdometerNegative -> R.string.editor_error_odometer_negative
    VehicleDraftError.CapacityNotPositive -> R.string.editor_error_capacity
    VehicleDraftError.RegistrationInvalid -> R.string.editor_error_registration
    VehicleDraftError.ClaimedNotPositive -> R.string.editor_error_claimed
}

private fun normLabel(norm: EmissionNorm): Int = when (norm) {
    EmissionNorm.BS3 -> R.string.norm_bs3
    EmissionNorm.BS4 -> R.string.norm_bs4
    EmissionNorm.BS6 -> R.string.norm_bs6
}

/** Tiles shown on the form, in design order. Van and truck share one tile; "Other" lives under edit. */
private val PICKER_TYPES = listOf(
    VehicleType.Car,
    VehicleType.Scooter,
    VehicleType.Motorcycle,
    VehicleType.AutoRickshaw,
    VehicleType.Truck,
)

private const val EXPANDED_ROTATION = 180f

package app.fuelwise.feature.settings.units

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwChoiceSheet
import app.fuelwise.core.designsystem.component.FwSectionHeader
import app.fuelwise.core.designsystem.component.FwSettingsRow
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.domain.units.EfficiencyUnit
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.format.efficiencyUnit
import app.fuelwise.feature.settings.R
import app.fuelwise.feature.settings.unitsSummary

/** Everything the Units screen can ask for. */
internal data class UnitsActions(
    val onBack: () -> Unit = {},
    val onChange: ((DisplayUnits) -> DisplayUnits) -> Unit = {},
    val onOwnUnits: (Boolean) -> Unit = {},
    val onOpenVehicle: (String) -> Unit = {},
)

@Composable
internal fun UnitsScreen(
    onBack: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    viewModel: UnitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UnitsContent(
        state,
        UnitsActions(
            onBack = onBack,
            onChange = viewModel::update,
            onOwnUnits = viewModel::setOwnUnits,
            onOpenVehicle = onOpenVehicle,
        ),
    )
}

// R1 offers km/L or L/100 km only; km and litres are fixed (owner decision, 20 Oct 2026). Miles, gallons and mpg
// stay in core:domain for a later release.
private val R1_EFFICIENCY = listOf(EfficiencyUnit.DISTANCE_PER_QUANTITY, EfficiencyUnit.QUANTITY_PER_100_DISTANCE)

@Composable
internal fun UnitsContent(state: UnitsUiState, actions: UnitsActions) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val vehicle = state.vehicle
    // A vehicle following the default shows the default greyed out until it gets its own units.
    val editable = vehicle == null || vehicle.override != null
    val shown = state.shown
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = vehicle?.let { stringResource(R.string.units_vehicle_title, it.vehicle.name) }
                ?: stringResource(R.string.units_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.settings_back),
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = FwTheme.spacing.gutter)
                .padding(bottom = FwTheme.spacing.xxl),
        ) {
            if (vehicle != null) {
                FwSwitchRow(
                    title = stringResource(R.string.units_own),
                    subtitle = stringResource(R.string.units_own_body),
                    checked = vehicle.override != null,
                    onCheckedChange = actions.onOwnUnits,
                )
            }
            val quantity = vehicle?.vehicle?.primaryTank?.energyType?.canonicalUnit
            FwSettingsRow(
                stringResource(R.string.units_efficiency),
                // On a vehicle, show its own fuel's unit ("kWh/100 km"), not the whole family.
                value = quantity?.let { efficiencyUnit(it, shown) }
                    ?: stringResource(efficiencyLabel(shown.efficiency)),
                onClick = if (editable) ({ picking = true }) else null,
            )
            Note(R.string.units_storage_note)
            if (state.vehicles.isNotEmpty()) {
                FwSectionHeader(
                    stringResource(R.string.units_per_vehicle),
                    Modifier.padding(top = FwTheme.spacing.xl, bottom = FwTheme.spacing.xxs),
                )
                state.vehicles.forEach { row ->
                    FwSettingsRow(
                        row.vehicle.name,
                        value = row.override?.let {
                            unitsSummaryFor(it, row.vehicle.primaryTank?.energyType?.canonicalUnit)
                        }
                            ?: stringResource(R.string.units_same_as_default),
                        onClick = { actions.onOpenVehicle(row.vehicle.id) },
                    )
                }
            }
        }
    }
    if (picking) {
        Picker(
            R.string.units_efficiency,
            R1_EFFICIENCY.map { stringResource(efficiencyLabel(it)) },
            R1_EFFICIENCY.indexOf(shown.efficiency).coerceAtLeast(0),
            onDismiss = { picking = false },
        ) { i -> actions.onChange { it.copy(efficiency = R1_EFFICIENCY[i]) } }
    }
}

@Composable
private fun Picker(
    title: Int,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    onPick: (Int) -> Unit,
) {
    FwChoiceSheet(
        title = stringResource(title),
        subtitle = subtitle,
        options = options,
        selectedIndex = selected,
        onSelect = {
            onPick(it)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text),
        style = FwTheme.type.bodyS,
        color = FwTheme.colors.ink2,
        modifier = Modifier.padding(top = FwTheme.spacing.s),
    )
}

/** A vehicle's own mileage unit, in its own fuel ("kWh/100 km" for an EV). */
@Composable
private fun unitsSummaryFor(units: DisplayUnits, quantity: QuantityUnit?): String =
    quantity?.let { efficiencyUnit(it, units) } ?: unitsSummary(units)

private fun efficiencyLabel(unit: EfficiencyUnit): Int = when (unit) {
    EfficiencyUnit.DISTANCE_PER_QUANTITY -> R.string.units_efficiency_distance

    EfficiencyUnit.QUANTITY_PER_100_DISTANCE -> R.string.units_efficiency_per100

    // Not offered in R1 (see R1_EFFICIENCY).
    EfficiencyUnit.MPG_US, EfficiencyUnit.MPG_UK -> R.string.units_efficiency_distance
}

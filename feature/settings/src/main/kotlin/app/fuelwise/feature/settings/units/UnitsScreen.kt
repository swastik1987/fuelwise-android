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
import app.fuelwise.core.domain.units.DistanceUnit
import app.fuelwise.core.domain.units.EfficiencyUnit
import app.fuelwise.core.domain.units.VolumeUnit
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

private enum class UnitField { Distance, Volume, Efficiency }

@Composable
internal fun UnitsContent(state: UnitsUiState, actions: UnitsActions) {
    var picking by rememberSaveable { mutableStateOf<UnitField?>(null) }
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
                stringResource(R.string.units_distance),
                value = stringResource(distanceLabel(shown.distance)),
                onClick = if (editable) ({ picking = UnitField.Distance }) else null,
            )
            FwSettingsRow(
                stringResource(R.string.units_volume),
                value = stringResource(volumeLabel(shown.volume)),
                onClick = if (editable) ({ picking = UnitField.Volume }) else null,
            )
            FwSettingsRow(
                stringResource(R.string.units_efficiency),
                // On a vehicle, show its own fuel's unit ("kWh/100 km"), not the whole family.
                value = quantity?.let { efficiencyUnit(it, shown) } ?: stringResource(efficiencyLabel(shown.efficiency)),
                onClick = if (editable) ({ picking = UnitField.Efficiency }) else null,
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
                        value = row.override?.let { unitsSummaryFor(it, row.vehicle.primaryTank?.energyType?.canonicalUnit) }
                            ?: stringResource(R.string.units_same_as_default),
                        onClick = { actions.onOpenVehicle(row.vehicle.id) },
                    )
                }
            }
        }
    }
    when (picking) {
        UnitField.Distance -> Picker(
            R.string.units_distance,
            DistanceUnit.entries.map { stringResource(distanceLabel(it)) },
            shown.distance.ordinal,
            onDismiss = { picking = null },
        ) { i -> actions.onChange { it.copy(distance = DistanceUnit.entries[i]) } }

        UnitField.Volume -> Picker(
            R.string.units_volume,
            VolumeUnit.entries.map { stringResource(volumeLabel(it)) },
            shown.volume.ordinal,
            onDismiss = { picking = null },
        ) { i -> actions.onChange { it.copy(volume = VolumeUnit.entries[i]) } }

        UnitField.Efficiency -> Picker(
            R.string.units_efficiency,
            EfficiencyUnit.entries.map { stringResource(efficiencyLabel(it)) },
            shown.efficiency.ordinal,
            subtitle = stringResource(R.string.units_efficiency_note),
            onDismiss = { picking = null },
        ) { i -> actions.onChange { it.copy(efficiency = EfficiencyUnit.entries[i]) } }

        null -> Unit
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

/** A vehicle's own units in one line, with its own fuel's mileage unit. */
@Composable
private fun unitsSummaryFor(units: DisplayUnits, quantity: QuantityUnit?): String =
    if (quantity == null) unitsSummary(units) else unitsSummary(units).substringBeforeLast(" · ") +
        " · " + efficiencyUnit(quantity, units)

private fun distanceLabel(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRE -> R.string.units_distance_km
    DistanceUnit.MILE -> R.string.units_distance_mi
}

private fun volumeLabel(unit: VolumeUnit): Int = when (unit) {
    VolumeUnit.LITRE -> R.string.units_volume_litre
    VolumeUnit.US_GALLON -> R.string.units_volume_us
    VolumeUnit.UK_GALLON -> R.string.units_volume_uk
}

private fun efficiencyLabel(unit: EfficiencyUnit): Int = when (unit) {
    EfficiencyUnit.DISTANCE_PER_QUANTITY -> R.string.units_efficiency_distance
    EfficiencyUnit.QUANTITY_PER_100_DISTANCE -> R.string.units_efficiency_per100
    EfficiencyUnit.MPG_US -> R.string.units_efficiency_mpg_us
    EfficiencyUnit.MPG_UK -> R.string.units_efficiency_mpg_uk
}

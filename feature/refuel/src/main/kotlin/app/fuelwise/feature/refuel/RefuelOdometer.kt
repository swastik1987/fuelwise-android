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
import androidx.compose.foundation.layout.width
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
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.entry.EntryDateTimePicker
import app.fuelwise.core.ui.entry.EntryLabels
import app.fuelwise.core.ui.entry.entryDateTimeLabel
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.price.fairPriceText
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.ZoneId
import kotlin.math.roundToInt

// The odometer part of the fill-up sheet (design.md screen 6, PRD F-02.4).

@Composable
internal fun OdometerSection(state: RefuelFormState, actions: RefuelActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FwOverline(stringResource(R.string.refuel_odometer))
        if (state.previousFillOdometer != null && !state.isEdit) {
            FwSegmentedControl(
                options = listOf(
                    stringResource(R.string.refuel_mode_odometer),
                    stringResource(R.string.refuel_trip_meter),
                ),
                selectedIndex = if (state.tripMeter == null) 0 else 1,
                onSelect = { actions.onTripMeterMode(it == 1) },
            )
        }
        val trip = state.tripMeter
        if (trip != null && state.previousFillOdometer != null) {
            TripMeterRow(state, trip, state.previousFillOdometer, actions)
        } else {
            OdometerField(state, actions)
        }
        OdometerMessages(state)
    }
}

/** The trip meter since the last fill, and the odometer it works out to (PRD F-02.4). */
@Composable
private fun TripMeterRow(state: RefuelFormState, trip: String, previous: Double, actions: RefuelActions) {
    FwTriangleRow(
        label = stringResource(R.string.refuel_trip_meter_since),
        value = trip,
        unit = stringResource(app.fuelwise.core.ui.R.string.unit_km),
        isComputed = false,
        focused = state.focus == NumericField.Odometer,
        onClick = { actions.onFocus(NumericField.Odometer) },
        autoLabel = "",
    )
    Text(
        stringResource(
            R.string.refuel_trip_meter_result,
            IndianNumberFormat.decimal(previous, 1),
            state.odometer.toDoubleOrNull()?.let { IndianNumberFormat.decimal(it, 1) } ?: "—",
        ),
        style = FwTheme.type.bodyS,
        color = FwTheme.colors.ink2,
    )
}

@Composable
private fun OdometerField(state: RefuelFormState, actions: RefuelActions) {
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
}

/** Why the reading can't be saved yet, in either mode. */
@Composable
private fun OdometerMessages(state: RefuelFormState) {
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

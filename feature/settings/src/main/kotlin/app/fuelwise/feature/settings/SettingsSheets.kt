package app.fuelwise.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.UpiPayLink
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.domain.units.EfficiencyUnit
import app.fuelwise.core.model.Money
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.format.NumberInput

/** A plausible home tariff: above zero, at most ₹100/kWh. */
internal fun isValidTariff(tariff: Money?): Boolean = tariff != null && tariff.paise in 1..MAX_TARIFF_PAISE

private const val MAX_TARIFF_PAISE = 10_000L

/** Home charging tariff (PRD F-02.5, F-18): ₹ per kWh, kept as paise. Save with an empty field clears it. */
@Composable
internal fun TariffSheet(current: Money?, onSave: (Money?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.let(MoneyInput::plain).orEmpty()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val parsed = MoneyInput.parse(text)
    val error = if (tried && text.isNotBlank() && !isValidTariff(parsed)) {
        stringResource(R.string.settings_tariff_error)
    } else {
        null
    }
    SettingsSheet(
        title = stringResource(R.string.settings_tariff),
        subtitle = stringResource(R.string.settings_tariff_hint),
        onDismiss = onDismiss,
        onSave = {
            tried = true
            when {
                text.isBlank() -> {
                    onSave(null)
                    onDismiss()
                }

                isValidTariff(parsed) -> {
                    onSave(parsed)
                    onDismiss()
                }
            }
        },
    ) {
        FwTextField(
            value = text,
            onValueChange = { text = NumberInput.sanitize(it, maxDecimals = 2) },
            label = stringResource(R.string.settings_tariff_label),
            unit = stringResource(R.string.settings_tariff_unit),
            error = error,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** UPI ID and payee name for trip-split links (PRD F-07.4, F-18). Both empty clears them. */
@Composable
internal fun UpiSheet(vpa: String?, name: String?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var vpaText by rememberSaveable { mutableStateOf(vpa.orEmpty()) }
    var nameText by rememberSaveable { mutableStateOf(name.orEmpty()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val valid = vpaText.isBlank() || UpiPayLink.isValidVpa(vpaText)
    SettingsSheet(
        title = stringResource(R.string.settings_upi),
        subtitle = stringResource(R.string.settings_upi_hint),
        onDismiss = onDismiss,
        onSave = {
            tried = true
            if (valid) {
                onSave(vpaText, if (vpaText.isBlank()) "" else nameText)
                onDismiss()
            }
        },
    ) {
        FwTextField(
            value = vpaText,
            onValueChange = { vpaText = it.trim().take(MAX_VPA) },
            label = stringResource(R.string.settings_upi_vpa),
            placeholder = stringResource(R.string.settings_upi_vpa_placeholder),
            error = if (tried && !valid) stringResource(R.string.settings_upi_error) else null,
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            modifier = Modifier.fillMaxWidth(),
        )
        FwTextField(
            value = nameText,
            onValueChange = { nameText = it.take(MAX_NAME) },
            label = stringResource(R.string.settings_upi_name),
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val MAX_VPA = 256
private const val MAX_NAME = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.gutter)
                .padding(bottom = FwTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
        ) {
            Text(title, style = FwTheme.type.h3, color = FwTheme.colors.ink)
            Text(subtitle, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
            content()
            FwInkButton(stringResource(R.string.settings_save), onClick = onSave, modifier = Modifier.fillMaxWidth())
            FwTextButton(stringResource(R.string.settings_delete_cancel), onClick = onDismiss)
        }
    }
}

/**
 * "Delete all data" (PRD F-18): a typed confirmation, because it can't be undone, and the Drive option when Drive is
 * connected. The dialog window only hosts [DeleteAllForm], which tests render on their own.
 */
@Composable
internal fun DeleteAllDialog(
    driveConnected: Boolean,
    deleting: Boolean,
    onConfirm: (alsoDrive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!deleting) onDismiss() }) {
        Surface(shape = FwTheme.radius.card, color = FwTheme.colors.surface) {
            DeleteAllForm(driveConnected, deleting, onConfirm, onDismiss)
        }
    }
}

/** Whether the typed text matches the confirmation word, ignoring case and stray spaces. */
internal fun deleteConfirmed(typed: String, word: String): Boolean = typed.trim().equals(word, ignoreCase = true)

/** The delete confirmation itself. "Delete everything" stays disabled until the confirmation word matches. */
@Composable
internal fun DeleteAllForm(
    driveConnected: Boolean,
    deleting: Boolean,
    onConfirm: (alsoDrive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val word = stringResource(R.string.settings_delete_word)
    var typed by rememberSaveable { mutableStateOf("") }
    var alsoDrive by rememberSaveable { mutableStateOf(false) }
    val matches = deleteConfirmed(typed, word)
    Column(
        Modifier.padding(FwTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        Text(stringResource(R.string.settings_delete_title), style = FwTheme.type.h3, color = FwTheme.colors.ink)
        Text(
            stringResource(R.string.settings_delete_body),
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink2,
        )
        if (driveConnected) {
            FwSwitchRow(
                title = stringResource(R.string.settings_delete_drive),
                subtitle = stringResource(R.string.settings_delete_drive_body),
                checked = alsoDrive,
                onCheckedChange = { alsoDrive = it },
            )
        }
        FwTextField(
            value = typed,
            onValueChange = { typed = it.take(word.length + 2) },
            label = stringResource(R.string.settings_delete_type, word),
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text(stringResource(R.string.settings_delete_cancel), color = FwTheme.colors.ink)
            }
            TextButton(onClick = { onConfirm(alsoDrive && driveConnected) }, enabled = matches && !deleting) {
                Text(
                    stringResource(if (deleting) R.string.settings_deleting else R.string.settings_delete_confirm),
                    color = if (matches && !deleting) FwTheme.colors.negative else FwTheme.colors.ink3,
                )
            }
        }
    }
}

/** "km · L · km/L": the default units in one line. */
@Composable
// R1 fixes km and litres, so the summary is the mileage unit alone (owner decision, 20 Oct 2026).
internal fun unitsSummary(units: DisplayUnits): String = stringResource(efficiencyShort(units.efficiency))

internal fun efficiencyShort(unit: EfficiencyUnit): Int = when (unit) {
    EfficiencyUnit.DISTANCE_PER_QUANTITY -> app.fuelwise.core.ui.R.string.unit_km_per_l
    EfficiencyUnit.QUANTITY_PER_100_DISTANCE -> app.fuelwise.core.ui.R.string.unit_l_per_100km
    EfficiencyUnit.MPG_US -> app.fuelwise.core.ui.R.string.unit_mpg_us
    EfficiencyUnit.MPG_UK -> app.fuelwise.core.ui.R.string.unit_mpg_uk
}

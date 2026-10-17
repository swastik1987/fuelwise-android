package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/** Toggle in our tokens: ink track with a `bg` knob when on; outlined `surface-2` track when off. */
@Composable
fun FwSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val colors = FwTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = colors.ink,
            checkedThumbColor = colors.bg,
            checkedBorderColor = colors.ink,
            uncheckedTrackColor = colors.surface2,
            uncheckedThumbColor = colors.ink3,
            uncheckedBorderColor = colors.ink3,
        ),
    )
}

/** A settings-style row: title, one-line explanation, switch. The whole row toggles (48 dp+ target). */
@Composable
fun FwSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.m),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = FwTheme.type.body.copy(fontWeight = FontWeight.Medium), color = FwTheme.colors.ink)
            subtitle?.let { Text(it, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2) }
        }
        // The row owns the toggle semantics; the switch is decoration.
        FwSwitch(checked = checked, onCheckedChange = null)
    }
}

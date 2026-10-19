package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * − value + on a `surface-2` pill (design.md screen 19, people count). Buttons are 48 dp targets and disable at the
 * ends of [range]. [valueDescription] is read by TalkBack in place of the bare number ("3 people").
 */
@Composable
fun FwStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    decreaseDescription: String,
    increaseDescription: String,
    valueDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = FwTheme.colors
    Row(
        modifier = modifier.background(colors.surface2, FwTheme.radius.pill),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = { onValueChange(value - 1) }, enabled = value > range.first) {
            Icon(
                painterResource(FwIcons.Remove),
                decreaseDescription,
                tint = if (value > range.first) colors.ink else colors.ink3,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            value.toString(),
            style = FwTheme.type.h3,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp).clearAndSetSemantics { contentDescription = valueDescription },
        )
        IconButton(onClick = { onValueChange(value + 1) }, enabled = value < range.last) {
            Icon(
                painterResource(FwIcons.Add),
                increaseDescription,
                tint = if (value < range.last) colors.ink else colors.ink3,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

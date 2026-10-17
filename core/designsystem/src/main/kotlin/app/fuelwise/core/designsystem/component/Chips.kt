package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/** Filter / selection chip: `surface-2`, selected = `accent-soft` with accent text. */
@Composable
fun FwFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FwTheme.colors
    FwChipBase(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        container = if (selected) colors.accentSoft else colors.surface2,
        content = if (selected) colors.accent else colors.ink,
        border = null,
    )
}

/** In-sheet choice chip (grade, payment, charger): selected = 1.5 dp ink outline on `surface-2`. */
@Composable
fun FwChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FwTheme.colors
    FwChipBase(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        container = colors.surface2,
        content = colors.ink,
        border = if (selected) BorderStroke(1.5.dp, colors.ink) else null,
    )
}

@Composable
private fun FwChipBase(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    border: BorderStroke?,
) {
    // Surface(selected = …) adds selectable semantics and pads the touch target to 48 dp.
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = FwTheme.radius.pill,
        color = container,
        contentColor = content,
        border = border,
    ) {
        Box(Modifier.height(FwTheme.size.chipHeight).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = FwTheme.type.label)
        }
    }
}

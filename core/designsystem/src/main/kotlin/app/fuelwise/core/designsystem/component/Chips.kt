package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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

/** What a [FwStatusPill]'s dot says. */
enum class FwStatus { Positive, Neutral, Warning }

/**
 * Small status pill with a coloured dot (design.md "Tracking status pill"): green = on, grey = paused,
 * amber = needs attention. Tappable; the touch target is padded to 48 dp.
 */
@Composable
fun FwStatusPill(label: String, status: FwStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FwTheme.colors
    val dot = when (status) {
        FwStatus.Positive -> colors.positive
        FwStatus.Neutral -> colors.ink3
        FwStatus.Warning -> colors.warning
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = FwTheme.radius.pill,
        color = colors.surface2,
        contentColor = colors.ink,
    ) {
        Row(
            Modifier.height(FwTheme.size.chipHeight).padding(horizontal = FwTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(FwTheme.spacing.xs).background(dot, CircleShape))
            Spacer(Modifier.width(FwTheme.spacing.xs))
            Text(label, style = FwTheme.type.label)
        }
    }
}

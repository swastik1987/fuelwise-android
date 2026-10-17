package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

/** Direction of change, and whether that change is good. Lower cost is good; higher km/L is good. */
data class Delta(val label: String, val direction: Direction, val isImprovement: Boolean?) {
    enum class Direction { Up, Down, Flat }
}

/** Pill with an arrow glyph so meaning never relies on colour alone (docs/design.md §4.1). */
@Composable
fun FwDeltaChip(delta: Delta, modifier: Modifier = Modifier) {
    val colors = FwTheme.colors
    val tone = when (delta.isImprovement) {
        true -> colors.positive
        false -> colors.negative
        null -> colors.ink2
    }
    val glyph = when (delta.direction) {
        Delta.Direction.Up -> "▲"
        Delta.Direction.Down -> "▼"
        Delta.Direction.Flat -> "—"
    }
    Box(
        modifier = modifier
            .height(24.dp)
            .background(tone.copy(alpha = TINT_ALPHA), FwTheme.radius.pill)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$glyph ${delta.label}", style = FwTheme.type.caption.copy(fontWeight = FontWeight.Medium), color = tone)
    }
}

/** Overline label → big number + unit suffix (40% size, ink-2, baseline-aligned) → delta chip → "i". */
@Composable
fun FwHeroMetric(
    overline: String,
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    delta: Delta? = null,
    onInfoClick: (() -> Unit)? = null,
    infoContentDescription: String? = null,
    valueStyle: TextStyle = FwTheme.type.displayXl,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
        FwOverline(overline)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = valueStyle, color = FwTheme.colors.ink, modifier = Modifier.alignByBaseline())
            if (unit != null) {
                Text(
                    text = unit,
                    style = valueStyle.copy(
                        fontSize = valueStyle.fontSize * UNIT_SCALE,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (delta != null || onInfoClick != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (delta != null) FwDeltaChip(delta)
                if (onInfoClick != null) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            painter = painterResource(FwIcons.Info),
                            contentDescription = infoContentDescription,
                            tint = FwTheme.colors.ink2,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 2-up grid tile: overline, `h2` number with unit, caption, optional extra content (e.g. a sparkline). */
@Composable
fun FwMetricTile(
    overline: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    FwCard(
        modifier = modifier.widthIn(min = 156.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        FwOverline(overline)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 6.dp)) {
            Text(value, style = FwTheme.type.h2, color = FwTheme.colors.ink, modifier = Modifier.alignByBaseline())
            if (unit != null) {
                Text(
                    unit,
                    style = FwTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        extra?.invoke(this)
        if (caption != null) {
            Text(
                caption,
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Uppercase section label ("THIS MONTH"). */
@Composable
fun FwOverline(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = FwTheme.type.overline, color = FwTheme.colors.ink2, modifier = modifier)
}

/** Section header: overline on the left, optional text action on the right ("See all"). */
@Composable
fun FwSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FwOverline(title, Modifier.weight(1f))
        if (actionLabel != null && onAction != null) FwTextButton(actionLabel, onAction)
    }
}

private const val TINT_ALPHA = 0.12f
private const val UNIT_SCALE = 0.4f

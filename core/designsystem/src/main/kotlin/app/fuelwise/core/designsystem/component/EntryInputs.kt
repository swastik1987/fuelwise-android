package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * Entry sheet frame (docs/design.md §5 "Bottom sheet"): a strip of scrim above a 32 dp-radius surface with a grab
 * handle, an `h3` title, header chips and close. [footer] (Save + keypad) stays pinned in the thumb zone.
 */
@Composable
fun FwEntrySheet(
    title: String,
    onClose: () -> Unit,
    closeDescription: String,
    modifier: Modifier = Modifier,
    headerChips: @Composable RowScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FwTheme.colors
    Column(modifier.fillMaxSize().background(colors.scrim).windowInsetsPadding(WindowInsets.statusBars)) {
        Spacer(Modifier.height(30.dp))
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.surface, FwTheme.radius.sheet)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(colors.ink3, FwTheme.radius.pill))
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = FwTheme.type.h3, color = colors.ink, modifier = Modifier.weight(1f))
                headerChips()
                IconButton(onClick = onClose) {
                    Icon(painterResource(FwIcons.Close), contentDescription = closeDescription, tint = colors.ink)
                }
            }
            Column(Modifier.weight(1f)) { content() }
            footer()
        }
    }
}

/** Small header chip ("Safari", "Now · 16 Oct"). */
@Composable
fun FwHeaderChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: Int? = null) {
    Surface(onClick = onClick, modifier = modifier, shape = FwTheme.radius.pill, color = FwTheme.colors.surface2) {
        Row(
            Modifier.height(32.dp).padding(start = if (leadingIcon != null) 8.dp else 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(painterResource(leadingIcon), null, tint = FwTheme.colors.ink, modifier = Modifier.size(16.dp))
            }
            Text(
                label,
                style = FwTheme.type.label,
                color = FwTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Mechanical-odometer input (docs/design.md §5): Geist Mono digits in cells, the prediction faint until typed.
 * Typed digits fill from the right like a real odometer. Shows at least [minCells] cells.
 */
@Composable
fun FwOdometerField(
    typed: String,
    predicted: String?,
    focused: Boolean,
    error: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    minCells: Int = 6,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = FwTheme.colors
    val shown = typed.ifEmpty { predicted.orEmpty() }
    val cells = maxOf(minCells, shown.length)
    val padded = shown.padStart(cells, ' ')
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        padded.forEach { ch ->
            val border = when {
                error -> BorderStroke(1.5.dp, colors.negative)
                focused -> BorderStroke(1.5.dp, colors.ink)
                else -> BorderStroke(1.dp, colors.line)
            }
            Box(
                Modifier.weight(1f).height(48.dp).border(border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ch.toString(),
                    style = FwTheme.type.mono,
                    color = if (typed.isEmpty()) colors.ink3 else colors.ink,
                )
            }
        }
        trailing()
    }
}

/**
 * One row of the price triangle (docs/design.md §5): label (+ optional source caption) on the left, value on the
 * right. The computed field shows `ink-2` text with a "=" prefix and an "auto" tag; the focused field an ink outline.
 */
@Composable
fun FwTriangleRow(
    label: String,
    value: String,
    unit: String?,
    isComputed: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    autoLabel: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colors = FwTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(colors.surface2, FwTheme.radius.input)
            .then(if (focused) Modifier.border(1.5.dp, colors.ink, FwTheme.radius.input) else Modifier)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                selected = focused
                role = Role.Button
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FwTheme.type.bodyS, color = colors.ink2)
            if (caption != null) Text(caption, style = FwTheme.type.caption, color = colors.ink2)
        }
        if (isComputed && value.isNotEmpty()) {
            Text(
                autoLabel.uppercase(),
                style = FwTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                color = colors.ink2,
                modifier = Modifier
                    .border(1.dp, colors.line, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (isComputed && value.isNotEmpty()) "= $value" else value,
            style = FwTheme.type.h3.copy(fontWeight = if (isComputed) FontWeight.Medium else FontWeight.SemiBold),
            color = if (isComputed) colors.ink2 else colors.ink,
        )
        if (unit != null && value.isNotEmpty()) {
            Text(unit, style = FwTheme.type.label, color = colors.ink2, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Checkbox row with a title and an explanation ("I missed logging the previous fill-up"). */
@Composable
fun FwCheckboxRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .semantics(mergeDescendants = true) { selected = checked }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = androidx.compose.material3.CheckboxDefaults.colors(
                checkedColor = FwTheme.colors.ink,
                checkmarkColor = FwTheme.colors.bg,
                uncheckedColor = FwTheme.colors.ink2,
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = FwTheme.type.body, color = FwTheme.colors.ink)
            subtitle?.let { Text(it, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2) }
        }
    }
}

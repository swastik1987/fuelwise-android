package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * `surface-2` track with a `surface` thumb on the selected segment (Full tank / Partial, Date / km / Both).
 * Grows taller instead of clipping when labels wrap (Hindi, large font scale).
 */
@Composable
fun FwSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FwTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Segments share the tallest label's height without stretching to the parent's.
            .height(IntrinsicSize.Min)
            .heightIn(min = 44.dp)
            .background(colors.surface2, FwTheme.radius.segment)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .fillMaxHeight()
                    .clip(FwTheme.radius.segmentThumb)
                    .background(if (selected) colors.surface else Color.Transparent)
                    .selectable(selected = selected, onClick = { onSelect(index) }, role = Role.RadioButton)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = FwTheme.type.label,
                    color = if (selected) colors.ink else colors.ink2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

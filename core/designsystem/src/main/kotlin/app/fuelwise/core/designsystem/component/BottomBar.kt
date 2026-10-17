package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

data class FwNavItem(val label: String, val icon: Int, val selectedIcon: Int)

/**
 * Bottom navigation: two tabs, the raised "+" FAB, two tabs. Labels are always visible.
 * Active = filled icon + ink label; inactive = outline icon + ink-3 label.
 */
@Composable
fun FwBottomBar(
    items: List<FwNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onFabClick: () -> Unit,
    fabContentDescription: String,
    modifier: Modifier = Modifier,
) {
    require(items.size == NAV_ITEMS) { "Bottom bar expects $NAV_ITEMS tabs around the FAB" }
    val colors = FwTheme.colors
    Column(modifier.fillMaxWidth().background(colors.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(thickness = FwTheme.size.hairline, color = colors.line)
        Row(
            modifier = Modifier.fillMaxWidth().height(FwTheme.size.navBarHeight).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.take(2).forEachIndexed { i, item -> NavTab(item, i == selectedIndex) { onSelect(i) } }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                Fab(onFabClick, fabContentDescription)
            }
            items.drop(2).forEachIndexed { i, item -> NavTab(item, i + 2 == selectedIndex) { onSelect(i + 2) } }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavTab(
    item: FwNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FwTheme.colors
    val tint = if (selected) colors.ink else colors.ink3
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(if (selected) item.selectedIcon else item.icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(FwTheme.size.icon),
        )
        Text(
            item.label,
            style = FwTheme.type.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = tint,
        )
    }
}

@Composable
private fun Fab(onClick: () -> Unit, contentDescription: String) {
    val colors = FwTheme.colors
    // A 6 dp `bg` ring around the 64 dp FAB cuts it out of the bar, like a notch.
    val ring = RoundedCornerShape(26.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .offset(y = (-26).dp)
            .size(FwTheme.size.fab + 12.dp)
            .shadow(24.dp, ring, ambientColor = colors.ink, spotColor = colors.ink.copy(alpha = 0.08f)),
        shape = ring,
        color = colors.bg,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(FwTheme.size.fab).background(colors.accent, FwTheme.radius.fab),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(FwIcons.Add),
                    contentDescription = contentDescription,
                    tint = colors.onAccent,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private const val NAV_ITEMS = 4

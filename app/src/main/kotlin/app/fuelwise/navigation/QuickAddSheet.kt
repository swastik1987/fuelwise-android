package app.fuelwise.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.fuelwise.R
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

enum class QuickAdd { Fuel, Expense, Toll }

/**
 * Long-press menu on "+" (docs/design.md §5). Fuel opens Add charge for an EV automatically. Trip and Scan join
 * this menu with their features (M8, M10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(onPick: (QuickAdd) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = FwTheme.spacing.m)) {
            Option(FwIcons.Fuel, stringResource(R.string.quick_fuel)) { onPick(QuickAdd.Fuel) }
            Option(FwIcons.Service, stringResource(R.string.quick_expense)) { onPick(QuickAdd.Expense) }
            Option(FwIcons.Toll, stringResource(R.string.quick_toll)) { onPick(QuickAdd.Toll) }
        }
    }
}

@Composable
private fun Option(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 56.dp)
            .padding(horizontal = FwTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = FwTheme.colors.ink)
        Text(label, style = FwTheme.type.body, color = FwTheme.colors.ink)
    }
}

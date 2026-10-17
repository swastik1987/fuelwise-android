package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/** Snackbar host that draws [FwSnackbar]: dark `ink` pill, `bg` text, accent "Undo" (docs/design.md §5). */
@Composable
fun FwSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> FwSnackbar(data) }
}

@Composable
fun FwSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = FwTheme.colors
    Row(
        modifier = modifier
            .padding(horizontal = FwTheme.spacing.m, vertical = FwTheme.spacing.xs)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(colors.ink, FwTheme.radius.pill)
            .padding(start = FwTheme.spacing.l, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = FwTheme.type.bodyS,
            color = colors.bg,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        data.visuals.actionLabel?.let { action ->
            TextButton(onClick = data::performAction) {
                Text(action, style = FwTheme.type.bodyS.copy(fontWeight = FontWeight.SemiBold), color = colors.accent)
            }
        }
    }
}

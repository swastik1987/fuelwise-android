package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * "Show the math" sheet behind every "i" (docs/design.md §2 principle 4): what the number is, the formula, and the
 * data it used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FwInfoSheet(title: String, paragraphs: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
        ) {
            Text(title, style = FwTheme.type.h3, color = FwTheme.colors.ink)
            paragraphs.forEach { Text(it, style = FwTheme.type.body, color = FwTheme.colors.ink2) }
            Text("", modifier = Modifier.padding(bottom = FwTheme.spacing.m))
        }
    }
}

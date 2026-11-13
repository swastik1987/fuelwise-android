package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

/** A bottom sheet with a title and free content, scrolling when it's taller than the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FwSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        FwSheetContent(title, content)
    }
}

/** [FwSheet]'s body on its own, for screenshot tests (Robolectric can't show a modal sheet). */
@Composable
fun FwSheetContent(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
    ) {
        Text(title, style = FwTheme.type.h3, color = FwTheme.colors.ink)
        content()
        Spacer(Modifier.padding(bottom = FwTheme.spacing.m))
    }
}

/** One choice in a list: title, optional detail on the right and below, a check when [selected]. */
@Composable
fun FwOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = FwTheme.size.buttonHeight)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FwTheme.type.body, color = FwTheme.colors.ink)
            subtitle?.let { Text(it, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2) }
        }
        trailing?.let { Text(it, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2) }
        if (selected) Icon(painterResource(FwIcons.Check), null, tint = FwTheme.colors.accent)
    }
}

package app.fuelwise.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * A settings list row (design.md screen 29): optional icon, title, optional current value, chevron when it opens
 * something. 56 dp minimum; the whole row is the target. Without [onClick] it's a read-only row (e.g. Currency).
 */
@Composable
fun FwSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color = FwTheme.colors.ink,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.m),
    ) {
        icon?.let { Icon(painterResource(it), null, tint = FwTheme.colors.ink, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = FwTheme.type.body.copy(fontWeight = FontWeight.Medium), color = titleColor)
            value?.let { Text(it, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2) }
        }
        if (onClick != null) Icon(painterResource(FwIcons.ChevronRight), null, tint = FwTheme.colors.ink3)
    }
}

/** A single-choice bottom sheet: title, then one row per option with a check on the selected one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FwChoiceSheet(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = FwTheme.spacing.gutter)) {
            Text(title, style = FwTheme.type.h3, color = FwTheme.colors.ink)
            subtitle?.let {
                Text(
                    it,
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.padding(top = FwTheme.spacing.xxs),
                )
            }
            Column(Modifier.padding(top = FwTheme.spacing.xs).selectableGroup()) {
                options.forEachIndexed { index, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option,
                            style = FwTheme.type.body,
                            color = FwTheme.colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (index == selectedIndex) {
                            Icon(painterResource(FwIcons.Check), null, tint = FwTheme.colors.accent)
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = FwTheme.spacing.m))
        }
    }
}

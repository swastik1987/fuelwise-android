package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/** Single-stroke glyph, one-line headline, one sentence, one primary action (docs/design.md §5). */
@Composable
fun FwEmptyState(
    icon: Int,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = FwTheme.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        Box(
            Modifier.size(72.dp).border(1.5.dp, FwTheme.colors.ink3, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = FwTheme.colors.ink3,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(headline, style = FwTheme.type.h2, color = FwTheme.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = FwTheme.type.body, color = FwTheme.colors.ink2, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            FwPrimaryButton(actionLabel, onAction, Modifier.padding(top = FwTheme.spacing.s))
        }
    }
}

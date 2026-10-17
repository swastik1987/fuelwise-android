package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.fuelwise.core.designsystem.theme.FwTheme

/** Flat `surface` card: 24 dp radius, hairline border in light theme, no border in dark. No shadow. */
@Composable
fun FwCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(FwTheme.spacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FwTheme.colors
    val border = colors.cardBorder.takeIf { it != Color.Transparent }?.let { BorderStroke(FwTheme.size.hairline, it) }
    val body: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick == null) {
        Surface(modifier, shape = FwTheme.radius.card, color = colors.surface, border = border, content = body)
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = FwTheme.radius.card,
            color = colors.surface,
            border = border,
            content = body,
        )
    }
}

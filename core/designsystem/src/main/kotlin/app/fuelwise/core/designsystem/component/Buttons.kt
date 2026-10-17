package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/** Full-width 56 dp accent button. Use for the one primary action on a screen. */
@Composable
fun FwPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Int? = null,
) {
    FwFilledButton(text, onClick, modifier, enabled, leadingIcon, FwTheme.colors.accent, FwTheme.colors.onAccent)
}

/** Ink-filled button: a second strong action when accent is already used on the screen (design/README.md). */
@Composable
fun FwInkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Int? = null,
) {
    FwFilledButton(text, onClick, modifier, enabled, leadingIcon, FwTheme.colors.ink, FwTheme.colors.bg)
}

/** `surface-2` fill with ink text. */
@Composable
fun FwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Int? = null,
) {
    FwFilledButton(text, onClick, modifier, enabled, leadingIcon, FwTheme.colors.surface2, FwTheme.colors.ink)
}

/** Plain ink text action; underlined only while pressed. */
@Composable
fun FwTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = FwTheme.size.minTouchTarget),
        color = Color.Transparent,
        contentColor = if (enabled) FwTheme.colors.ink else FwTheme.colors.ink3,
        shape = FwTheme.radius.button,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FwTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                style = FwTheme.type.label,
                textDecoration = if (pressed) TextDecoration.Underline else TextDecoration.None,
                modifier = Modifier.padding(vertical = 15.dp),
            )
        }
    }
}

@Composable
private fun FwFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: Int?,
    container: Color,
    content: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = FwTheme.colors
    val fill = when {
        !enabled -> colors.surface2
        pressed -> lerp(container, Color.Black, PRESSED_DARKEN)
        else -> container
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(FwTheme.size.buttonHeight),
        shape = FwTheme.radius.button,
        color = fill,
        contentColor = if (enabled) content else colors.ink3,
        interactionSource = interaction,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs, Alignment.CenterHorizontally),
            modifier = Modifier.padding(horizontal = FwTheme.spacing.m),
        ) {
            if (leadingIcon != null) {
                Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Text(
                text = text,
                style = FwTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val PRESSED_DARKEN = 0.08f

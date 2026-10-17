package app.fuelwise.core.designsystem.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.R
import app.fuelwise.core.designsystem.theme.FwTheme

/** A key on [FwNumericKeypad]. */
sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey

    data object Dot : KeypadKey

    data object Backspace : KeypadKey
}

/**
 * In-sheet 3×4 keypad (docs/design.md §5). The system keyboard is never shown for numeric entry.
 * Keys are 54 dp in the fill-up sheet (design/README.md) and never below 48 dp. A light keyboard-tap haptic per press.
 */
@Composable
fun FwNumericKeypad(
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 54.dp,
    allowDot: Boolean = true,
    backspaceDescription: String,
) {
    val view = LocalView.current
    val press: (KeypadKey) -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onKey(it)
    }
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d ->
                    Key(Modifier.weight(1f), keyHeight, { press(KeypadKey.Digit(d)) }) { KeyLabel("$d") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (allowDot) {
                Key(Modifier.weight(1f), keyHeight, { press(KeypadKey.Dot) }) { KeyLabel(".") }
            } else {
                Box(Modifier.weight(1f))
            }
            Key(Modifier.weight(1f), keyHeight, { press(KeypadKey.Digit(0)) }) { KeyLabel("0") }
            Key(
                Modifier.weight(1f).semantics { contentDescription = backspaceDescription },
                keyHeight,
                { press(KeypadKey.Backspace) },
            ) {
                Icon(painterResource(R.drawable.fw_ic_backspace), contentDescription = null, tint = FwTheme.colors.ink)
            }
        }
    }
}

@Composable
private fun Key(modifier: Modifier, height: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = FwTheme.radius.segment,
        color = FwTheme.colors.surface2,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun KeyLabel(text: String) {
    Text(text, style = FwTheme.type.h2.copy(fontWeight = FontWeight.SemiBold), color = FwTheme.colors.ink)
}

/** "Typing · Quantity (L)" indicator and the "Next field" pill that sit above the keypad. */
@Composable
fun FwKeypadBar(focusLabel: String, nextLabel: String?, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(FwTheme.colors.ink, FwTheme.radius.pill))
        Text(
            focusLabel,
            style = FwTheme.type.caption,
            color = FwTheme.colors.ink2,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        if (nextLabel != null) {
            Surface(onClick = onNext, shape = FwTheme.radius.pill, color = FwTheme.colors.surface2) {
                Row(
                    Modifier.height(32.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(nextLabel, style = FwTheme.type.label, color = FwTheme.colors.ink)
                    Icon(
                        painterResource(R.drawable.fw_ic_arrow_forward),
                        contentDescription = null,
                        tint = FwTheme.colors.ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

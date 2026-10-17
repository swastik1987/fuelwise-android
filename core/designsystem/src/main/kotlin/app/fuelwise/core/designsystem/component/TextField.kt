package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * Filled input: `surface-2`, 16 dp radius, label inside the field, optional trailing unit ("km", "L").
 * Focused = 1.5 dp ink outline; error = negative outline plus the message below.
 */
@Composable
fun FwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    unit: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = FwTheme.type.body,
    singleLine: Boolean = true,
) {
    val colors = FwTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline = when {
        error != null -> BorderStroke(1.5.dp, colors.negative)
        focused -> BorderStroke(1.5.dp, colors.ink)
        else -> null
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = imeAction,
            ),
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (error != null) error(error) },
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .heightIn(min = 60.dp)
                        .background(colors.surface2, FwTheme.radius.input)
                        .then(if (outline != null) Modifier.border(outline, FwTheme.radius.input) else Modifier)
                        .padding(horizontal = FwTheme.spacing.m, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = FwTheme.type.caption, color = colors.ink2)
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = textStyle, color = colors.ink3)
                            }
                            inner()
                        }
                    }
                    if (unit != null) {
                        Text(
                            unit,
                            style = FwTheme.type.label,
                            color = colors.ink2,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            },
        )
        if (error != null) {
            Text(
                error,
                style = FwTheme.type.caption,
                color = colors.negative,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

package app.fuelwise.core.ui.format

import app.fuelwise.core.designsystem.component.KeypadKey

/**
 * Applies a keypad press to a numeric text buffer. Rejects keys that would make the number invalid instead of
 * clamping, so what's on screen is always exactly what was typed.
 */
object KeypadInput {
    fun apply(
        text: String,
        key: KeypadKey,
        maxDecimals: Int,
        maxIntegerDigits: Int = DEFAULT_MAX_INTEGER_DIGITS,
    ): String = when (key) {
        KeypadKey.Backspace -> text.dropLast(1)

        KeypadKey.Dot -> when {
            maxDecimals == 0 || '.' in text -> text
            text.isEmpty() -> "0."
            else -> "$text."
        }

        is KeypadKey.Digit -> {
            val candidate = if (text == "0") key.value.toString() else text + key.value
            val integer = candidate.substringBefore('.')
            val decimals = candidate.substringAfter('.', "")
            if (integer.length > maxIntegerDigits || decimals.length > maxDecimals) text else candidate
        }
    }

    private const val DEFAULT_MAX_INTEGER_DIGITS = 7
}

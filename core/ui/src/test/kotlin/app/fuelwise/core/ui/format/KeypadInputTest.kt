package app.fuelwise.core.ui.format

import app.fuelwise.core.designsystem.component.KeypadKey.Backspace
import app.fuelwise.core.designsystem.component.KeypadKey.Digit
import app.fuelwise.core.designsystem.component.KeypadKey.Dot
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class KeypadInputTest {
    private fun type(vararg keys: Any, maxDecimals: Int = 2, maxInt: Int = 7): String = keys.fold("") { text, k ->
        val key = when (k) {
            is Int -> Digit(k)
            "." -> Dot
            else -> Backspace
        }
        KeypadInput.apply(text, key, maxDecimals, maxInt)
    }

    @Test
    fun `types a decimal`() {
        assertThat(type(3, 2, ".", 4, 0)).isEqualTo("32.40")
    }

    @Test
    fun `extra decimals and a second dot are ignored`() {
        assertThat(type(1, ".", 2, 3, 4, ".")).isEqualTo("1.23")
    }

    @Test
    fun `leading dot becomes zero point`() {
        assertThat(type(".", 5)).isEqualTo("0.5")
    }

    @Test
    fun `leading zero is replaced`() {
        assertThat(type(0, 7)).isEqualTo("7")
    }

    @Test
    fun `integer-only fields reject the dot and cap length`() {
        assertThat(type(4, ".", 2, 3, 1, 8, maxDecimals = 0, maxInt = 4)).isEqualTo("4231")
    }

    @Test
    fun `backspace removes the last character`() {
        assertThat(type(4, 2, "<")).isEqualTo("4")
        assertThat(type("<")).isEmpty()
    }
}

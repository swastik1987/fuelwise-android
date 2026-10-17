package app.fuelwise.core.ui.format

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NumberInputTest {
    @Test
    fun `parses grouped and decimal numbers`() {
        assertThat(NumberInput.parseDecimal("42,318")).isEqualTo(42_318.0)
        assertThat(NumberInput.parseDecimal(" 12.5 ")).isEqualTo(12.5)
        assertThat(NumberInput.parseDecimal("1,23,456.78")).isEqualTo(123_456.78)
    }

    @Test
    fun `rejects empty and malformed input`() {
        assertThat(NumberInput.parseDecimal("")).isNull()
        assertThat(NumberInput.parseDecimal("1.2.3")).isNull()
        assertThat(NumberInput.parseDecimal("12a")).isNull()
        assertThat(NumberInput.parseDecimal("-5")).isNull()
    }

    @Test
    fun `sanitize keeps digits, one dot and the allowed decimals`() {
        assertThat(NumberInput.sanitize("4a2,3.1.8x9", maxDecimals = 1)).isEqualTo("423.1")
        assertThat(NumberInput.sanitize("40.567", maxDecimals = 2)).isEqualTo("40.56")
        assertThat(NumberInput.sanitize("42318.5", maxDecimals = 0)).isEqualTo("423185")
    }
}

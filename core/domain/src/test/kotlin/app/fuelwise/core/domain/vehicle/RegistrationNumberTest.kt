package app.fuelwise.core.domain.vehicle

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

// Sample numbers are the fictional ones from design.md §11.
class RegistrationNumberTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "KA 01 AB 1234", "ka-01-ab-1234", "MH02CD5678", "DL 3C EF 9012", "KA 05 GH 3456", "22 BH 1234 AA",
            "21bh0001a",
        ],
    )
    fun `accepts state and Bharat series numbers`(input: String) {
        assertThat(RegistrationNumber.isValid(input)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "KA01AB12345", "K01AB1234", "KA01ABCD1234", "22BH12AA", "22BH1234", "1234"])
    fun `rejects malformed numbers`(input: String) {
        assertThat(RegistrationNumber.isValid(input)).isFalse()
    }

    @ParameterizedTest
    @CsvSource(
        "KA01AB1234, KA 01 AB 1234",
        "ka-01 ab-1234, KA 01 AB 1234",
        "dl 3c ef 9012, DL 3C EF 9012",
        "22bh1234aa, 22 BH 1234 AA",
    )
    fun `formats for display`(input: String, expected: String) {
        assertThat(RegistrationNumber.format(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["KA 01 AB 1234", "ka01ab1234", "KA-01-AB-1234"])
    fun `normalizes to one comparable form`(input: String) {
        assertThat(RegistrationNumber.normalize(input)).isEqualTo("KA01AB1234")
    }
}

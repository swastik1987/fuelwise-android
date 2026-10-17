package app.fuelwise.core.ui.format

import app.fuelwise.core.model.Money
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IndianNumberFormatTest {
    @ParameterizedTest
    @CsvSource(
        "0, ₹0.00",
        "5, ₹0.05",
        "10292, ₹102.92",
        "333461, '₹3,334.61'",
        "12345678, '₹1,23,456.78'",
        "184500000, '₹18,45,000.00'",
        "1000000000000, '₹10,00,00,00,000.00'",
    )
    fun `rupees with lakh grouping`(paise: Long, expected: String) {
        assertThat(IndianNumberFormat.rupees(Money(paise))).isEqualTo(expected)
    }

    @Test
    fun `negative amounts use a minus sign`() {
        assertThat(IndianNumberFormat.rupees(Money(-16500))).isEqualTo("−₹165.00")
    }

    @ParameterizedTest
    @CsvSource("624000, '₹6,240'", "333461, '₹3,335'", "333449, '₹3,334'", "49, ₹0")
    fun `whole rupees round half up`(paise: Long, expected: String) {
        assertThat(IndianNumberFormat.rupeesWhole(Money(paise))).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource("999, 999", "1294, '1,294'", "42318, '42,318'", "123456, '1,23,456'", "-123456, '-1,23,456'")
    fun `groups integers`(value: Long, expected: String) {
        assertThat(IndianNumberFormat.group(value)).isEqualTo(expected)
    }

    @Test
    fun `decimals round half up and keep grouping`() {
        assertThat(IndianNumberFormat.decimal(42318.456, 1)).isEqualTo("42,318.5")
        assertThat(IndianNumberFormat.decimal(14.2857, 3)).isEqualTo("14.286")
        assertThat(IndianNumberFormat.decimal(-0.04, 1)).isEqualTo("0.0")
        assertThat(IndianNumberFormat.decimal(-0.5, 1)).isEqualTo("-0.5")
        assertThat(IndianNumberFormat.decimal(310.0, 0)).isEqualTo("310")
    }

    @Test
    fun `dates read 16 Oct 2026`() {
        assertThat(IndianNumberFormat.date(LocalDate.of(2026, 10, 16))).isEqualTo("16 Oct 2026")
    }
}

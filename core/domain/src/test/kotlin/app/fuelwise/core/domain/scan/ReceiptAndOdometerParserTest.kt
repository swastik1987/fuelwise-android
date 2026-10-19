package app.fuelwise.core.domain.scan

import app.fuelwise.core.domain.scan.ScanFixtures.at
import app.fuelwise.core.domain.scan.ScanFixtures.lines
import app.fuelwise.core.model.Money
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import org.junit.jupiter.api.Test

class ReceiptAndOdometerParserTest {
    @Test
    fun `petrol receipt - date, amounts and station`() {
        val r = ReceiptParser.parse(ScanFixtures.receipt)
        assertThat(r.date).isEqualTo(LocalDate.of(2026, 10, 16))
        assertThat(r.time).isEqualTo(LocalTime.of(18, 42))
        assertThat(r.amounts.validated).isTrue()
        assertThat(r.amounts.total?.value).isEqualTo(Money(200_000))
        assertThat(r.amounts.quantity?.value).isEqualTo(19.43)
        assertThat(r.amounts.unitPrice?.value).isEqualTo(Money(10_292))
        assertThat(r.stationName).isEqualTo("HP Service Station")
    }

    @Test
    fun `CNG receipt - value under its label, AM time, dealer name under the brand`() {
        val r = ReceiptParser.parse(ScanFixtures.cngReceipt)
        assertThat(r.date).isEqualTo(LocalDate.of(2026, 9, 3))
        assertThat(r.time).isEqualTo(LocalTime.of(8, 15))
        assertThat(r.amounts.validated).isTrue()
        assertThat(r.amounts.total?.value).isEqualTo(Money(45_000))
        assertThat(r.amounts.quantity?.value).isEqualTo(5.09)
        assertThat(r.amounts.unitPrice?.value).isEqualTo(Money(8_840))
        assertThat(r.stationName).isEqualTo("Shree Ganesh Fuels")
    }

    @Test
    fun `receipt read as columns - rows rebuilt from boxes, named month`() {
        val r = ReceiptParser.parse(ScanFixtures.columnReceipt)
        assertThat(r.date).isEqualTo(LocalDate.of(2026, 10, 16))
        assertThat(r.time).isEqualTo(LocalTime.of(19, 5))
        assertThat(r.amounts.validated).isTrue()
        assertThat(r.amounts.total?.value).isEqualTo(Money(300_000))
        assertThat(r.amounts.quantity?.value).isEqualTo(28.79)
        assertThat(r.amounts.unitPrice?.value).isEqualTo(Money(10_421))
        assertThat(r.stationName).isEqualTo("Sai Baba Automobiles")
    }

    @Test
    fun `month-first dates and PM times`() {
        val r = ReceiptParser.parse(lines("Date: 10/16/26 07:30 PM"))
        assertThat(r.date).isEqualTo(LocalDate.of(2026, 10, 16))
        assertThat(r.time).isEqualTo(LocalTime.of(19, 30))
    }

    @Test
    fun `an unreadable receipt is empty`() {
        assertThat(ReceiptParser.parse(lines("~~~", "...")).isEmpty).isTrue()
    }

    @Test
    fun `odometer next to km wins over trip meter and clock`() {
        val km = OdometerParser.parse(lines("12:45", "24°C", "TRIP A 123.4 km", "ODO 42318 km"))
        assertThat(km?.value).isEqualTo(42_318L)
    }

    @Test
    fun `mechanical odometer with leading zero and km on the next row`() {
        assertThat(OdometerParser.parse(lines("042318", "km"))?.value).isEqualTo(42_318L)
    }

    @Test
    fun `trip meter with more digits loses to the ODO label`() {
        assertThat(OdometerParser.parse(lines("TRIP 1234.5 km", "ODO 56789 km"))?.value).isEqualTo(56_789L)
    }

    @Test
    fun `7-segment B read as 8, range readout ignored`() {
        assertThat(OdometerParser.parse(lines("Range 320 km", "4231B km"))?.value).isEqualTo(42_318L)
    }

    @Test
    fun `grouped and spaced thousands`() {
        assertThat(OdometerParser.parse(lines("1,23,456 km"))?.value).isEqualTo(123_456L)
        assertThat(OdometerParser.parse(lines("42 318 km"))?.value).isEqualTo(42_318L)
    }

    @Test
    fun `speed in km per hour is not the km glyph`() {
        val km = OdometerParser.parse(listOf(at("1200 km/h", 0, 0, width = 200), at("15872", 3, 0), at("km", 3, 1)))
        assertThat(km?.value).isEqualTo(15_872L)
    }

    @Test
    fun `prediction breaks a tie between two runs`() {
        val km = OdometerParser.parse(lines("31842", "42318"), predictedKm = 42_100.0)
        assertThat(km?.value).isEqualTo(42_318L)
    }

    @Test
    fun `fewer than four digits is not an odometer`() {
        assertThat(OdometerParser.parse(lines("320 km", "24"))).isNull()
    }
}

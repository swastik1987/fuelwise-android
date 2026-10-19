package app.fuelwise.core.domain.scan

import app.fuelwise.core.domain.scan.ScanFixtures.lines
import app.fuelwise.core.model.Money
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class PumpMeterParserTest {
    @TestFactory
    fun `reads and validates every pump display fixture`() = ScanFixtures.pumpDisplays.map { pump ->
        DynamicTest.dynamicTest(pump.name) {
            val reading = PumpMeterParser.parse(pump.lines)
            assertThat(reading.validated).isTrue()
            assertThat(reading.total?.value).isEqualTo(Money(pump.total))
            assertThat(reading.quantity?.value).isEqualTo(pump.quantity)
            assertThat(reading.unitPrice?.value).isEqualTo(Money(pump.rate))
        }
    }

    @Test
    fun `auto-fill rate across the fixture set`() {
        // TODO(F-15 AC): with the 50 real photos in the fixture set this is the PRD target (≥85% all three correct).
        val correct = ScanFixtures.pumpDisplays.count { pump ->
            val r = PumpMeterParser.parse(pump.lines)
            r.validated && r.total?.value == Money(pump.total) && r.quantity?.value == pump.quantity &&
                r.unitPrice?.value == Money(pump.rate)
        }
        assertThat(correct * 100 / ScanFixtures.pumpDisplays.size).isAtLeast(85)
    }

    @Test
    fun `a misread that breaks the triangle is offered but not validated`() {
        // 19.48 × 102.92 = 2,004.88, ₹4.88 away from ₹2,000.
        val reading = PumpMeterParser.parse(lines("AMOUNT 2000.00", "VOLUME 19.48", "RATE 102.92"))
        assertThat(reading.validated).isFalse()
        assertThat(reading.total?.value).isEqualTo(Money(200_000))
        assertThat(reading.quantity?.value).isEqualTo(19.48)
        assertThat(reading.unitPrice?.value).isEqualTo(Money(10_292))
    }

    @Test
    fun `unlabelled numbers that don't fit are not guessed`() {
        val reading = PumpMeterParser.parse(lines("2000.00", "19.48", "102.92"))
        assertThat(reading.validated).isFalse()
        assertThat(reading.isEmpty).isTrue()
    }

    @Test
    fun `two labelled fields are offered when the third is unreadable`() {
        val reading = PumpMeterParser.parse(lines("AMOUNT 2000.00", "VOLUME ##.##", "RATE 102.92"))
        assertThat(reading.validated).isFalse()
        assertThat(reading.total?.value).isEqualTo(Money(200_000))
        assertThat(reading.quantity).isNull()
        assertThat(reading.unitPrice?.value).isEqualTo(Money(10_292))
    }

    @Test
    fun `glare and labels only read as nothing`() {
        assertThat(PumpMeterParser.parse(lines("AMOUNT", "VOLUME", "RATE")).isEmpty).isTrue()
        assertThat(PumpMeterParser.parse(emptyList()).isEmpty).isTrue()
    }

    @Test
    fun `the rate hint picks the reading near the price last paid`() {
        // 1000.00 / 10.00 / 100.00 and 1000.00 / 9.72 / 102.88 both pass; the last price paid decides.
        val text = lines("1000.00", "10.00", "100.00", "9.72", "102.88")
        val reading = PumpMeterParser.parse(text, rateHint = Money(10_288))
        assertThat(reading.validated).isTrue()
        assertThat(reading.unitPrice?.value).isEqualTo(Money(10_288))
        assertThat(reading.quantity?.value).isEqualTo(9.72)
    }

    @Test
    fun `the tolerance is the PRD's one rupee`() {
        // 19.43 × 102.92 = 1,999.74: ₹0.26 off passes; ₹1.01 off doesn't.
        assertThat(PumpMeterParser.parse(lines("AMOUNT 2000.00", "VOLUME 19.43", "RATE 102.92")).validated)
            .isTrue()
        assertThat(PumpMeterParser.parse(lines("AMOUNT 2000.75", "VOLUME 19.43", "RATE 102.92")).validated)
            .isFalse()
    }

    @Test
    fun `boxes come back for the live labels`() {
        val reading = PumpMeterParser.parse(ScanFixtures.pumpDisplays.first { it.lines.first().box != null }.lines)
        assertThat(reading.total?.box).isNotNull()
        assertThat(reading.quantity?.box).isNotNull()
        assertThat(reading.unitPrice?.box).isNotNull()
    }
}

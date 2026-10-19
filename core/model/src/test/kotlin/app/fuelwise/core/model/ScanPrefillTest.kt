package app.fuelwise.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScanPrefillTest {
    @Test
    fun `round-trips every field, including awkward station names`() {
        val prefill = ScanPrefill(
            total = Money(200_000),
            quantity = 19.43,
            unitPrice = Money(10_292),
            odometerKm = 42_318,
            dateTimeUtcMs = 1_792_150_000_000,
            stationName = "HP & Sons; Indiranagar = 100 ft Rd",
            photoToken = "0192f3a4-0000-7000-8000-000000000001",
        )
        assertThat(ScanPrefill.decode(prefill.encode())).isEqualTo(prefill)
    }

    @Test
    fun `an odometer-only scan carries just the odometer`() {
        assertThat(ScanPrefill.decode(ScanPrefill(odometerKm = 15_872).encode()))
            .isEqualTo(ScanPrefill(odometerKm = 15_872))
    }

    @Test
    fun `junk decodes to an empty prefill`() {
        assertThat(ScanPrefill.decode("total=abc&=x&nonsense")).isEqualTo(ScanPrefill())
    }
}

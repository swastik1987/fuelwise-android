package app.fuelwise.core.domain.entry

import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TripMeterTest {
    private fun fill(id: String, at: Long, odo: Double, tank: String = "petrol") =
        Refuel(id, "ertiga", tank, at, "Asia/Kolkata", odo, 30.0, Money(10_250), Money(307_500), isFull = true)

    @Test
    fun `adds the trip meter to the latest fill before the entry, on any tank`() {
        val fills =
            listOf(fill("a", 1_000, 31_045.0), fill("b", 2_000, 31_420.5, tank = "cng"), fill("c", 3_000, 31_800.0))
        val previous = previousFillOdometer(fills, atUtcMs = 2_500)
        assertThat(previous).isEqualTo(31_420.5)
        assertThat(odometerFromTripMeter(previous!!, 212.4)).isWithin(1e-9).of(31_632.9)
        // Editing "b" itself looks at the fill before it.
        assertThat(previousFillOdometer(fills, atUtcMs = 2_000, excludeId = "b")).isEqualTo(31_045.0)
        assertThat(previousFillOdometer(fills, atUtcMs = 500)).isNull()
    }
}

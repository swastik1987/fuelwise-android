package app.fuelwise.core.domain.engine

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class PredictionsTest {
    private val now = Instant.parse("2026-10-16T04:30:00Z")
    private val predictions = ComputePredictions(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `km per day from the last 90 days`() {
        val readings = listOf(
            reading(30_000.0, daysAgo = 200), // outside the window, ignored
            reading(41_000.0, daysAgo = 60),
            reading(42_200.0, daysAgo = 0),
        )
        assertThat(predictions.avgKmPerDay(readings)!!).isWithin(0.001).of(20.0)
    }

    @Test
    fun `falls back to all time when the recent window is too short`() {
        val readings = listOf(
            reading(40_000.0, daysAgo = 100),
            reading(41_000.0, daysAgo = 3),
            reading(41_060.0, daysAgo = 0), // recent span is only 3 days
        )
        assertThat(predictions.avgKmPerDay(readings)!!).isWithin(0.001).of(10.6)
    }

    @Test
    fun `needs two readings at least seven days apart`() {
        assertThat(predictions.avgKmPerDay(listOf(reading(1_000.0, 0)))).isNull()
        assertThat(predictions.avgKmPerDay(listOf(reading(1_000.0, 6), reading(1_300.0, 0)))).isNull()
    }

    @Test
    fun `predicted odometer adds driving since the last reading`() {
        val readings = listOf(reading(42_000.0, daysAgo = 40), reading(42_318.0, daysAgo = 10))
        // 318 km / 30 days = 10.6 km/day; 10 days since → 42,424
        assertThat(predictions.predictedOdometer(readings)!!).isWithin(0.001).of(42_424.0)
    }

    @Test
    fun `predicted odometer without a rate is the last reading`() {
        assertThat(predictions.predictedOdometer(listOf(reading(9_870.0, daysAgo = 2)))).isEqualTo(9_870.0)
        assertThat(predictions.predictedOdometer(emptyList())).isNull()
    }

    @Test
    fun `predicted date for a km-based reminder`() {
        val readings = listOf(reading(41_900.0, daysAgo = 20), reading(42_100.0, daysAgo = 0)) // 10 km/day
        val due = predictions.predictedTimeForOdometer(readings, targetKm = 42_520.0)!!
        assertThat(due).isEqualTo(now.toEpochMilli() + 42 * DAY_MS)
        assertThat(predictions.predictedTimeForOdometer(readings, targetKm = 42_000.0)).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun `range left only after a full fill`() {
        val full = FillUp("f", 42_000.0, 0, 50.0, isFull = true)
        // Safari: 50 L × 13.4 km/L = 670 km, 360 km driven → 310 km left
        assertThat(predictions.rangeLeftKm(50.0, 13.4, full, 42_360.0)!!).isWithin(0.001).of(310.0)
        assertThat(predictions.rangeLeftKm(50.0, 13.4, full.copy(isFull = false), 42_360.0)).isNull()
        assertThat(predictions.rangeLeftKm(50.0, 13.4, full, 43_000.0)).isEqualTo(0.0)
        assertThat(predictions.rangeLeftKm(null, 13.4, full, 42_360.0)).isNull()
    }

    private fun reading(odo: Double, daysAgo: Long) = OdometerReading(odo, now.toEpochMilli() - daysAgo * DAY_MS)

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

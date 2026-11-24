package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.Money
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Trips card thumbnails and figures (owner choice 24 Nov 2026, option B). Points around Whitefield → HSR Layout. */
class RouteSketchTest {
    private fun point(i: Int, lat: Double, lng: Double) = TrackPoint(i * 5_000L, lat, lng, 8f, 10f, 0)

    @Test
    fun `a route fits the unit square with its aspect kept and centred`() {
        // 0.1° east, 0.05° north: wider than tall, so it spans x fully and sits in the middle vertically.
        val route = (0..10).map { point(it, 12.90 + it * 0.005, 77.70 - it * 0.01) }
        val sketch = requireNotNull(RouteSketch.of(route))
        assertThat(sketch.xs.min()).isWithin(1e-6f).of(0f)
        assertThat(sketch.xs.max()).isWithin(1e-6f).of(1f)
        assertThat(sketch.ys.min()).isGreaterThan(0.2f)
        assertThat(sketch.ys.max()).isLessThan(0.8f)
        // Heading north-west: starts bottom right, ends top left (y grows downwards).
        assertThat(sketch.xs.first()).isGreaterThan(sketch.xs.last())
        assertThat(sketch.ys.first()).isGreaterThan(sketch.ys.last())
    }

    @Test
    fun `long routes are thinned but keep their last point`() {
        val route = (0 until 500).map { point(it, 12.90 + it * 0.0001, 77.60 + it * 0.0001) }
        val sketch = requireNotNull(RouteSketch.of(route))
        assertThat(sketch.size).isAtMost(RouteSketch.MAX_POINTS + 1)
        // Northmost point, and the taller span here, so it touches the top edge.
        assertThat(sketch.ys.last()).isWithin(1e-6f).of(0f)
    }

    @Test
    fun `nothing to draw gives no sketch`() {
        assertThat(RouteSketch.of(emptyList())).isNull()
        assertThat(RouteSketch.of(listOf(point(0, 12.9, 77.6)))).isNull()
        assertThat(RouteSketch.of(listOf(point(0, 12.9, 77.6), point(1, 12.9, 77.6)))).isNull()
    }

    @Test
    fun `average speed uses recorded time, else start to end`() {
        val gps =
            Trip("t", "v", 0, "Asia/Kolkata", 23.1, TripPurpose.Commute, endUtcMs = 7_200_000, recordedSeconds = 3_480)
        assertThat(averageSpeedKmh(gps)).isWithin(0.01).of(23.9)
        val manual = gps.copy(recordedSeconds = null)
        assertThat(averageSpeedKmh(manual)).isWithin(0.01).of(11.55)
        assertThat(averageSpeedKmh(manual.copy(endUtcMs = null))).isNull()
    }

    @Test
    fun `cost per km rounds to the paisa`() {
        assertThat(costPerKm(Money(15_300), 23.1)).isEqualTo(Money(662))
        assertThat(costPerKm(null, 23.1)).isNull()
        assertThat(costPerKm(Money(15_300), 0.0)).isNull()
    }
}

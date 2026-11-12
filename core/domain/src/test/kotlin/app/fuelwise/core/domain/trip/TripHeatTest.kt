package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TripHeatTest {
    // 0.001° of latitude ≈ 111 m.
    private fun at(lat: Double, segment: Int = 0) = TrackPoint(0, lat, 77.6400, 8f, null, segment)

    @Test
    fun `a straight stretch is filled in about every 50 m`() {
        val heat = densifyRoute(listOf(at(12.9000), at(12.9010)))
        // 111 m → 3 steps: both ends plus two between.
        assertThat(heat).hasSize(4)
        assertThat(heat.first().lat).isEqualTo(12.9000)
        assertThat(heat.last().lat).isEqualTo(12.9010)
        assertThat(heat[1].lat).isWithin(1e-9).of(12.9000 + 0.001 / 3)
    }

    @Test
    fun `a pause is not bridged and an empty route has no heat`() {
        val heat = densifyRoute(listOf(at(12.9000), at(12.9100, segment = 1)))
        assertThat(heat).hasSize(2)
        assertThat(densifyRoute(emptyList())).isEmpty()
    }

    @Test
    fun `heat is capped by keeping every n-th point`() {
        val many = (0 until 50).map { HeatPoint(it.toDouble(), 0.0) }
        assertThat(capHeat(many, max = 10)).hasSize(10)
        assertThat(capHeat(many, max = 10).map { it.lat }).containsAtLeast(0.0, 5.0, 45.0)
        assertThat(capHeat(many, max = 100)).isSameInstanceAs(many)
    }

    @Test
    fun `places visited twice or more, busiest first`() {
        val home = HeatPoint(12.9100, 77.6400)
        val office = HeatPoint(12.9780, 77.7500)
        val ends = listOf(
            home,
            office,
            HeatPoint(12.9105, 77.6400), // 55 m from home: the same place
            office,
            HeatPoint(12.9101, 77.6401),
            HeatPoint(13.0500, 77.5000), // once only
        )
        val places = frequentPlaces(ends)
        assertThat(places.map { it.visits }).containsExactly(3, 2).inOrder()
        assertThat(places.first().lat).isWithin(0.001).of(12.9102)
        assertThat(places.last().lat).isEqualTo(12.9780)
    }
}

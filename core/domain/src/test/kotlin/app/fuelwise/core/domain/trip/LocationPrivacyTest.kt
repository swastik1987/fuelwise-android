package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LocationPrivacyTest {
    // Home in HSR Layout; 0.001° of latitude ≈ 111 m.
    private val home = PrivatePlace("home", "Home", 12.9100, 77.6400)
    private fun at(lat: Double) = TrackPoint(0, lat, 77.6400, 8f, null, 0)

    @Test
    fun `route ends inside a private place are trimmed, the middle is kept`() {
        val route = listOf(
            at(12.9100), // home
            at(12.9110), // 111 m: still inside 200 m
            at(12.9130), // 333 m: outside
            at(12.9200),
            at(12.9105), // passing home mid-way... but it's also the end, so trimmed
        )
        assertThat(trimPrivateEnds(route, listOf(home))).containsExactly(at(12.9130), at(12.9200)).inOrder()
        // A stretch near home in the middle stays.
        val through = listOf(at(12.9200), at(12.9100), at(12.9300))
        assertThat(trimPrivateEnds(through, listOf(home))).isEqualTo(through)
    }

    @Test
    fun `no places changes nothing, and a route never leaving home is emptied`() {
        val route = listOf(at(12.9100), at(12.9300))
        assertThat(trimPrivateEnds(route, emptyList())).isEqualTo(route)
        assertThat(trimPrivateEnds(listOf(at(12.9100), at(12.9105)), listOf(home))).isEmpty()
    }

    @Test
    fun `retention cutoffs`() {
        val now = 100L * 24 * 60 * 60 * 1_000
        assertThat(RouteRetention.DEFAULT).isEqualTo(RouteRetention.Days90)
        assertThat(RouteRetention.Days30.cutoffUtcMs(now)).isEqualTo(70L * 24 * 60 * 60 * 1_000)
        assertThat(RouteRetention.Forever.cutoffUtcMs(now)).isNull()
    }
}

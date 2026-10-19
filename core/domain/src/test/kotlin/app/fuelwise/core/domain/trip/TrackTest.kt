package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TrackTest {
    // Along a meridian, 0.001° of latitude ≈ 111.2 m anywhere on Earth.
    private val stepM = distanceM(12.9, 77.6, 12.901, 77.6)

    @Test
    fun `haversine matches a known distance`() {
        // MG Road to Indiranagar 100 ft Road, Bengaluru: about 3.9 km as the crow flies.
        assertThat(distanceM(12.9756, 77.6050, 12.9719, 77.6412)).isWithin(50.0).of(3_940.0)
        assertThat(stepM).isWithin(0.5).of(111.2)
    }

    @Test
    fun `distance, time, idle and max speed`() {
        val points = listOf(
            fix(0, 12.900, speed = 20f),
            fix(5, 12.901, speed = 22f),
            fix(10, 12.902, speed = 22f),
            // Stood still at a signal for 60 s.
            fix(70, 12.902),
            fix(75, 12.903, speed = 21f),
        )
        val s = summarizeTrack(points)
        assertThat(s.distanceKm).isWithin(0.001).of(3 * stepM / 1_000)
        assertThat(s.recordedSeconds).isEqualTo(75)
        assertThat(s.idleSeconds).isEqualTo(60)
        assertThat(s.maxSpeedKmh!!).isWithin(0.01).of(22 * 3.6)
        assertThat(s.averageKmh!!).isWithin(0.1).of(3 * stepM / 1_000 / (75 / 3_600.0))
    }

    @Test
    fun `inaccurate fixes and GPS jumps are dropped`() {
        val points = listOf(
            fix(0, 12.900),
            fix(5, 12.901),
            // 80 m accuracy: ignored.
            fix(7, 12.950, accuracy = 80f),
            // 5.5 km in 5 s: a jump, ignored.
            fix(10, 12.950),
            fix(15, 12.902),
        )
        val s = summarizeTrack(points)
        assertThat(s.distanceKm).isWithin(0.001).of(2 * stepM / 1_000)
        assertThat(s.recordedSeconds).isEqualTo(15)
    }

    @Test
    fun `a pause is neither distance nor time`() {
        val points = listOf(
            fix(0, 12.900),
            fix(5, 12.901),
            // Paused for 20 minutes and moved 1 km meanwhile.
            fix(1_205, 12.910, segment = 1),
            fix(1_210, 12.911, segment = 1),
        )
        val s = summarizeTrack(points)
        assertThat(s.distanceKm).isWithin(0.001).of(2 * stepM / 1_000)
        assertThat(s.recordedSeconds).isEqualTo(10)
    }

    @Test
    fun `no maths on an empty or single-point track`() {
        assertThat(summarizeTrack(emptyList())).isEqualTo(TrackSummary.EMPTY)
        assertThat(summarizeTrack(listOf(fix(0, 12.9))).distanceKm).isEqualTo(0.0)
        assertThat(TrackSummary.EMPTY.averageKmh).isNull()
    }

    @Test
    fun `straight line simplifies to its ends, a corner is kept`() {
        val straight = (0..20).map { fix(it * 5L, 12.900 + it * 0.0001) }
        assertThat(simplifyTrack(straight)).containsExactly(straight.first(), straight.last()).inOrder()

        val corner = listOf(fix(0, 12.900), fix(5, 12.901), fix(10, 12.902), fix(15, 12.902, lng = 77.601))
        assertThat(simplifyTrack(corner).map { it.atUtcMs }).containsExactly(0L, 10_000L, 15_000L).inOrder()
    }

    @Test
    fun `small wobble under the tolerance goes, segments stay apart`() {
        // ~2 m sideways wobble on a 500 m line.
        val wobble = listOf(
            fix(0, 12.900),
            fix(5, 12.901, lng = 77.60002),
            fix(10, 12.902),
            fix(15, 12.903, lng = 77.59998),
            fix(20, 12.9045),
        )
        assertThat(simplifyTrack(wobble)).hasSize(2)
        val twoSegments = wobble.map { it.copy(segment = if (it.atUtcMs > 10_000) 1 else 0) }
        assertThat(simplifyTrack(twoSegments).map { it.segment }).containsExactly(0, 0, 1, 1).inOrder()
    }

    @Test
    fun `codec round-trips, missing speed included`() {
        val points = listOf(fix(0, 12.9, speed = 13.5f), fix(5, 12.901), fix(10, 12.902, segment = 2))
        assertThat(TrackCodec.decode(TrackCodec.encode(points))).isEqualTo(points)
        assertThat(TrackCodec.decode(TrackCodec.encode(emptyList()))).isEmpty()
    }

    private fun fix(
        seconds: Long,
        lat: Double,
        lng: Double = 77.6,
        accuracy: Float = 8f,
        speed: Float? = null,
        segment: Int = 0,
    ) = TrackPoint(seconds * 1_000, lat, lng, accuracy, speed, segment)
}

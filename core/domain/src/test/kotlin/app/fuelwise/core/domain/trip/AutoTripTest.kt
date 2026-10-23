package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Test

class AutoTripTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val minute = 60_000L

    // 0.0001° of latitude ≈ 11 m; 0.001° ≈ 111 m.
    private fun fix(atMin: Double, lat: Double, accuracy: Float = 10f) =
        TrackPoint((atMin * minute).toLong(), lat, 77.6, accuracy, null, 0)

    @Test
    fun `still after five minutes within 50 m`() {
        val d = StillDetector(startUtcMs = 0)
        d.add(listOf(fix(0.0, 12.900), fix(1.0, 12.902), fix(2.0, 12.904)))
        // Creeping in a queue: 30 m in 4 minutes isn't movement.
        d.add(listOf(fix(4.0, 12.9042), fix(6.0, 12.9043)))
        assertThat(d.isStill(6 * minute)).isFalse()
        assertThat(d.isStill(7 * minute)).isTrue()
        assertThat(d.lastMovedUtcMs).isEqualTo(2 * minute)
    }

    @Test
    fun `moving again resets the clock, inaccurate fixes don't`() {
        val d = StillDetector(startUtcMs = 0)
        d.add(listOf(fix(0.0, 12.900)))
        // A 200 m jump with 80 m accuracy is GPS noise, not driving.
        d.add(listOf(fix(4.0, 12.902, accuracy = 80f)))
        assertThat(d.isStill(5 * minute)).isTrue()
        d.add(listOf(fix(5.5, 12.902)))
        assertThat(d.isStill(6 * minute)).isFalse()
        assertThat(d.isStill(11 * minute)).isTrue()
    }

    @Test
    fun `with no fixes at all the trip start counts as the last movement`() {
        val d = StillDetector(startUtcMs = 10 * minute)
        assertThat(d.isStill(14 * minute)).isFalse()
        assertThat(d.isStill(15 * minute)).isTrue()
    }

    @Test
    fun `short automatic trips are discarded`() {
        val s = TrackSummary(distanceKm = 0.6, recordedSeconds = 0, idleSeconds = 0, maxSpeedKmh = null)
        assertThat(keepAutoTrip(s, 0, 3 * minute)).isTrue()
        assertThat(keepAutoTrip(s, 0, 90_000)).isFalse()
        assertThat(keepAutoTrip(s.copy(distanceKm = 0.4), 0, 10 * minute)).isFalse()
    }

    private fun trip(vehicleId: String, at: LocalDateTime, source: TripSource = TripSource.Auto) = Trip(
        id = "t-$vehicleId-$at",
        vehicleId = vehicleId,
        startUtcMs = at.atZone(ist).toInstant().toEpochMilli(),
        zoneId = ist.id,
        distanceKm = 12.0,
        purpose = TripPurpose.Commute,
        source = source,
    )

    private fun ms(at: LocalDateTime) = at.atZone(ist).toInstant().toEpochMilli()

    private fun at(at: LocalDateTime) = at.atZone(ist)

    // Monday 20 Oct 2026 onwards.
    private val monday = LocalDateTime.of(2026, 10, 19, 9, 0)
    private val active = setOf("scooter", "car")

    @Test
    fun `bluetooth wins over habit and default`() {
        val history = (0L..4L).map { trip("scooter", monday.plusDays(it)) }
        val v = assignAutoTripVehicle("car", at(monday.plusWeeks(1)), history, active, "scooter")
        assertThat(v).isEqualTo("car")
    }

    @Test
    fun `weekday morning habit picks the scooter`() {
        // Scooter to the office at 9; the car for the weekend.
        val history = (0L..4L).map { trip("scooter", monday.plusDays(it).plusMinutes(it * 10)) } +
            listOf(trip("car", monday.plusDays(5)), trip("car", monday.plusDays(6)))
        val nextMonday = at(monday.plusWeeks(1).plusMinutes(20))
        assertThat(assignAutoTripVehicle(null, nextMonday, history, active, "car")).isEqualTo("scooter")
        // Saturday morning: only two car trips, too few to call it a habit, so the default.
        val saturday = at(monday.plusDays(12))
        assertThat(assignAutoTripVehicle(null, saturday, history, active, "car")).isEqualTo("car")
    }

    @Test
    fun `no clear majority falls back to the default`() {
        val history = listOf(
            trip("scooter", monday),
            trip("car", monday.plusDays(1)),
            trip("scooter", monday.plusDays(2)),
            trip("car", monday.plusDays(3)),
        )
        assertThat(assignAutoTripVehicle(null, at(monday.plusWeeks(1)), history, active, "car")).isEqualTo("car")
    }

    @Test
    fun `manual trips and archived vehicles don't count, and the habit wraps midnight`() {
        val late = LocalDateTime.of(2026, 10, 19, 23, 40)
        val history = (0L..2L).map { trip("old", late.plusDays(it)) } +
            (0L..2L).map { trip("scooter", late.plusDays(it), TripSource.Manual) } +
            (0L..2L).map { trip("car", late.plusDays(it).plusMinutes(30)) }
        // 00:05 on Thursday is 15–35 minutes from those 23:40 and 00:10 starts.
        val v = assignAutoTripVehicle(null, at(LocalDateTime.of(2026, 10, 22, 0, 5)), history, active, "scooter")
        assertThat(v).isEqualTo("car")
    }

    @Test
    fun `without a usable default the first active vehicle`() {
        assertThat(assignAutoTripVehicle(null, at(monday), emptyList(), setOf("car"), "sold")).isEqualTo("car")
        assertThat(assignAutoTripVehicle(null, at(monday), emptyList(), emptySet(), null)).isNull()
    }

    @Test
    fun `pause ends`() {
        val now = ms(LocalDateTime.of(2026, 10, 23, 22, 30))
        assertThat(autoTripPauseEnd(AutoTripPause.OneHour, now, ist)).isEqualTo(now + 60 * minute)
        assertThat(autoTripPauseEnd(AutoTripPause.UntilTomorrow, now, ist))
            .isEqualTo(ms(LocalDateTime.of(2026, 10, 24, 5, 0)))
        assertThat(autoTripPauseEnd(AutoTripPause.UntilTurnedOn, now, ist)).isEqualTo(Long.MAX_VALUE)
    }
}

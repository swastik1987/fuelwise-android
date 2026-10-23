package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripStatus
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Test

class TripLinkTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(day: Int, hour: Int) =
        LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun trip(
        id: String,
        start: Long,
        end: Long?,
        vehicle: String = "safari",
        status: TripStatus = TripStatus.Done,
    ) = Trip(id, vehicle, start, zone.id, 140.0, TripPurpose.Personal, endUtcMs = end, status = status)

    @Test
    fun aTripUnderWayComesFirstThenTheNearest() {
        val mysuru = trip("mysuru", at(12, 7), at(12, 11))
        val office = trip("office", at(13, 9), at(13, 10))
        val earlier = trip("earlier", at(9, 9), at(9, 10))
        // A toll at 9:30 on the 12th, on the way to Mysuru.
        assertThat(tripsToLink(listOf(earlier, office, mysuru), "safari", at(12, 9)).map { it.id })
            .containsExactly("mysuru", "office", "earlier").inOrder()
    }

    @Test
    fun onlyTheVehiclesFinishedTripsInsideTheWindow() {
        val other = trip("nexon", at(12, 7), at(12, 11), vehicle = "nexon")
        val live = trip("live", at(12, 7), null, status = TripStatus.Recording)
        val old = trip("old", at(1, 7), at(1, 8))
        // A manual trip has no end: it covers its start day.
        val manual = trip("manual", at(12, 0), null)
        assertThat(tripsToLink(listOf(other, live, old, manual), "safari", at(12, 18)).map { it.id })
            .containsExactly("manual")
    }
}

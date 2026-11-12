package app.fuelwise.core.location

import app.fuelwise.core.domain.trip.AddressParts
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.testing.FakeLocationPrivacyRepository
import app.fuelwise.core.testing.FakeTripRepository
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TripPlaceNamesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-11-12T06:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val trips = FakeTripRepository()
    private val privacy = FakeLocationPrivacyRepository()

    /** What was asked, and a neighbourhood per rounded latitude. */
    private val asked = mutableListOf<Pair<Double, Double>>()
    private val lookup = object : PlaceLookup {
        override suspend fun lookup(lat: Double, lng: Double): AddressParts? {
            asked += lat to lng
            return when (lat) {
                12.972 -> AddressParts(subLocality = "Indiranagar", locality = "Bengaluru")
                12.97 -> AddressParts(subLocality = "Whitefield", locality = "Bengaluru")
                else -> null
            }
        }
    }

    private fun TestScope.namer() = RouteTripPlaceNames(trips, privacy, lookup, clock, backgroundScope)

    private fun point(lat: Double, lng: Double) = TrackPoint(0, lat, lng, 8f, null, 0)

    private suspend fun recorded(
        id: String,
        route: List<TrackPoint>,
        daysAgo: Long = 0,
        from: String? = null,
        source: TripSource = TripSource.Auto,
    ) {
        val start = now.toEpochMilli() - daysAgo * 86_400_000L - 3_600_000L
        val trip = Trip(
            id = id,
            vehicleId = "safari",
            startUtcMs = start,
            zoneId = zone.id,
            distanceKm = 12.0,
            purpose = TripPurpose.Personal,
            endUtcMs = start + 1_800_000L,
            fromPlace = from,
            source = source,
        )
        trips.finishRecording(trip, route)
    }

    private val drive = listOf(point(12.971598, 77.640800), point(12.9800, 77.7000), point(12.969800, 77.750100))

    @Test
    fun `both ends are named from rounded points`() = runTest {
        recorded("t1", drive)
        namer().nameMissing(all = false)
        val trip = trips.trips.single()
        assertThat(trip.fromPlace).isEqualTo("Indiranagar")
        assertThat(trip.toPlace).isEqualTo("Whitefield")
        // Only the two ends, rounded to 3 decimals; never the route.
        assertThat(asked).containsExactly(12.972 to 77.641, 12.97 to 77.75).inOrder()
    }

    @Test
    fun `a private place names its end without a lookup, and a typed name is kept`() = runTest {
        privacy.addPrivatePlace("Home", 12.9700, 77.7500)
        recorded("t1", drive, from = "Mum's place")
        namer().nameMissing(all = false)
        val trip = trips.trips.single()
        assertThat(trip.fromPlace).isEqualTo("Mum's place")
        assertThat(trip.toPlace).isEqualTo("Home")
        assertThat(asked).isEmpty()
    }

    @Test
    fun `off, manual trips and older trips are left alone unless asked for all`() = runTest {
        recorded("manual", drive, source = TripSource.Manual)
        recorded("old", drive, daysAgo = 30)
        namer().nameMissing(all = false)
        assertThat(trips.trips.all { it.fromPlace == null }).isTrue()

        namer().nameMissing(all = true)
        assertThat(trips.trips.single { it.id == "old" }.fromPlace).isEqualTo("Indiranagar")
        assertThat(trips.trips.single { it.id == "manual" }.fromPlace).isNull()

        privacy.setPlaceNames(false)
        recorded("new", drive)
        asked.clear()
        namer().nameMissing(all = true)
        assertThat(trips.trips.single { it.id == "new" }.fromPlace).isNull()
        assertThat(asked).isEmpty()
    }

    @Test
    fun `no answer leaves the end empty for the next try`() = runTest {
        recorded("t1", listOf(point(19.07, 72.87), point(19.08, 72.88)))
        namer().nameMissing(all = false)
        assertThat(trips.trips.single().fromPlace).isNull()
        assertThat(trips.trips.single().toPlace).isNull()
    }
}

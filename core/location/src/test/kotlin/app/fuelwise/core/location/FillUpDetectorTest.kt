package app.fuelwise.core.location

import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.station.StationFuel
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.testing.FakeAutoTripSettingsRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeFillUpPromptRepository
import app.fuelwise.core.testing.FakeStationRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FillUpDetectorTest {
    private val minute = 60_000L
    private val start = SampleEntries.at(2026, 10, 22, hour = 9)
    private val now = start + 30 * minute
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val settings = FakeAutoTripSettingsRepository()
    private val promptState = FakeFillUpPromptRepository()
    private val posted = mutableListOf<Pair<String, Long>>()
    private val notifier = object : FillUpNotifier {
        override fun post(vehicle: Vehicle, station: Station, atUtcMs: Long) {
            posted += station.label.orEmpty() to atUtcMs
        }
    }

    // HP in Indiranagar, and the car standing 10 m from it for 6 minutes mid-drive.
    private val hp = Station(42, 12.97842, 77.64081, "HP", null, setOf(StationFuel.Petrol, StationFuel.Diesel), false)
    private val drive = listOf(
        fix(0.0, 12.9700),
        fix(3.0, 12.9750),
        fix(6.0, 12.97850),
        fix(6.5, 12.97855),
        fix(12.0, 12.9800),
        fix(15.0, 12.9900),
    )
    private val trip = Trip("t", "safari", start, "UTC", 3.0, TripPurpose.Personal, source = TripSource.Auto)

    private fun fix(atMin: Double, lat: Double) =
        TrackPoint(start + (atMin * minute).toLong(), lat, 77.64081, 8f, null, 0)

    private fun detector(refuels: List<app.fuelwise.core.model.Refuel> = emptyList()) = FillUpDetector(
        settings,
        FakeStationRepository(listOf(hp)),
        promptState,
        FakeVehicleRepository(listOf(SampleVehicles.safari)),
        FakeEntryRepository(refuels = refuels),
        notifier,
        clock,
    )

    @Test
    fun `a stop at a station gets one prompt, then the 6-hour limit holds`() = runTest {
        settings.setEnabled(true)
        detector().afterTrip(trip, drive)
        assertThat(posted).containsExactly("HP" to start + 6 * minute)
        assertThat(promptState.state.value.lastPromptUtcMs).containsExactly("safari", now)
        detector().afterTrip(trip, drive)
        assertThat(posted).hasSize(1)
    }

    @Test
    fun `nothing while automatic trips are off, after three rejections, or when already logged`() = runTest {
        detector().afterTrip(trip, drive)
        assertThat(posted).isEmpty()

        settings.setEnabled(true)
        repeat(3) { promptState.recordRejection(42) }
        detector().afterTrip(trip, drive)
        assertThat(posted).isEmpty()

        val fresh = FakeFillUpPromptRepository()
        val logged = SampleEntries.safariRefuels.first().copy(vehicleId = "safari", dateTimeUtcMs = start + 20 * minute)
        FillUpDetector(
            settings,
            FakeStationRepository(listOf(hp)),
            fresh,
            FakeVehicleRepository(listOf(SampleVehicles.safari)),
            FakeEntryRepository(refuels = listOf(logged)),
            notifier,
            clock,
        ).afterTrip(trip, drive)
        assertThat(posted).isEmpty()
    }
}

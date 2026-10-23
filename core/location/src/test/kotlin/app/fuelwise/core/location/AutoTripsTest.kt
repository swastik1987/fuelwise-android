package app.fuelwise.core.location

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.trip.EstimateTripCost
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.testing.FakeAutoTripSettingsRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoTripsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = SampleEntries.at(2026, 10, 19, hour = 8)
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), zone)
    private val settings = FakeAutoTripSettingsRepository()
    private val detection = FakeDetection()
    private val trips = FakeTripRepository()
    private val starts = mutableListOf<String>()
    private val starter = object : AutoTripStarter {
        override fun startAutomaticRecording() {
            starts += "service"
        }
    }
    private val prompts = object : TripPrompts {
        override fun odometerAfterTrip(tripId: String, distanceKm: Double) = Unit

        override fun startTripPrompt() {
            starts += "prompt"
        }
    }

    private fun TestScope.subject(): Pair<AutoTrips, TripRecorder> {
        val vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari))
        val recorder = TripRecorder(
            trips,
            vehicles,
            FakeEntryRepository(refuels = SampleEntries.safariRefuels),
            EstimateTripCost(ComputeConsumption()),
            object : TripLocationSource {
                override val hasPermission = true

                // Never ends by itself, like a real request within its lifetime.
                override fun fixes(lifetimeMs: Long): Flow<List<TrackPoint>> = flow { awaitCancellation() }

                override fun flush() = Unit
            },
            object : RecordingHost {
                override fun startForegroundRecording() = Unit
            },
            prompts,
            clock,
            zone,
            backgroundScope,
        )
        val auto = AutoTrips(
            settings,
            detection,
            recorder,
            vehicles,
            FakeUserDataRepository(),
            trips,
            starter,
            prompts,
            clock,
            zone,
        )
        return auto to recorder
    }

    @Test
    fun `nothing happens until automatic trips are turned on`() = runTest {
        val (auto, _) = subject()
        auto.onDriving()
        assertThat(starts).isEmpty()
        // The event still counts for the battery-saver health check (PRD F-21.7).
        assertThat(settings.settings.value.lastActivityEventUtcMs).isEqualTo(now)
    }

    @Test
    fun `with background location a drive starts recording on its own`() = runTest {
        val (auto, _) = subject()
        settings.setEnabled(true)
        auto.onDriving()
        assertThat(starts).containsExactly("service")
    }

    @Test
    fun `without all-the-time access, or one-tap chosen, it asks instead`() = runTest {
        val (auto, _) = subject()
        settings.setEnabled(true)
        detection.background = false
        auto.onDriving()
        detection.background = true
        settings.setEnabled(true, oneTap = true)
        auto.onDriving()
        assertThat(starts).containsExactly("prompt", "prompt")
    }

    @Test
    fun `paused, or already recording, ignores the drive`() = runTest {
        val (auto, recorder) = subject()
        settings.setEnabled(true)
        settings.pauseUntil(now + 1)
        auto.onDriving()
        assertThat(starts).isEmpty()

        settings.pauseUntil(null)
        recorder.start("safari")
        runCurrent()
        auto.onDriving()
        assertThat(starts).isEmpty()
    }

    @Test
    fun `the service starts an automatic trip for the selected vehicle`() = runTest {
        val (auto, recorder) = subject()
        val id = auto.startAutomaticTrip()
        runCurrent()
        assertThat(id).isNotNull()
        val active = recorder.state.value as RecordingState.Active
        assertThat(active.source).isEqualTo(TripSource.Auto)
        assertThat(active.vehicleId).isEqualTo("safari")
        assertThat(trips.observeTrip(id!!).first()?.source).isEqualTo(TripSource.Auto)
    }

    @Test
    fun `detection is registered only while on and permitted`() = runTest {
        val (auto, _) = subject()
        auto.syncRegistration()
        assertThat(detection.registered).isFalse()
        settings.setEnabled(true)
        auto.syncRegistration()
        assertThat(detection.registered).isTrue()
        detection.permission = false
        auto.syncRegistration()
        assertThat(detection.registered).isFalse()
    }

    private class FakeDetection : DriveDetection {
        var permission = true
        var background = true
        var registered = false

        override val hasPermission get() = permission
        override val hasBackgroundLocation get() = background

        override fun register() {
            registered = true
        }

        override fun unregister() {
            registered = false
        }
    }
}

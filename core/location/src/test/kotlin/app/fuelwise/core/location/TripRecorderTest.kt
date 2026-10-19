package app.fuelwise.core.location

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.trip.EstimateTripCost
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

// runCurrent and advanceTimeBy drive the recorder's background work on virtual time.
@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 19, hour = 8)), zone)
    private val trips = FakeTripRepository()
    private val source = FakeSource()
    private val host = object : RecordingHost {
        var starts = 0

        override fun startForegroundRecording() {
            starts++
        }
    }

    private fun TestScope.recorder(repo: FakeTripRepository = trips) = TripRecorder(
        repo,
        FakeVehicleRepository(listOf(SampleVehicles.safari)),
        FakeEntryRepository(refuels = SampleEntries.safariRefuels),
        EstimateTripCost(ComputeConsumption()),
        source,
        host,
        clock,
        zone,
        backgroundScope,
    )

    @Test
    fun `record, pause, resume and stop`() = runTest {
        val recorder = recorder()
        val id = recorder.start("safari")
        runCurrent()
        assertThat(host.starts).isEqualTo(1)
        assertThat(source.listening).isEqualTo(1)
        assertThat(trips.trips.single().status).isEqualTo(TripStatus.Recording)
        assertThat(recorder.start("safari")).isEqualTo(id)

        send(listOf(fix(0, 12.900), fix(5, 12.901), fix(10, 12.902)))
        val live = recorder.state.value as RecordingState.Active
        assertThat(live.summary.distanceKm).isWithin(0.001).of(0.222)
        assertThat(trips.points(id)).hasSize(3)

        recorder.pause()
        advanceTimeBy(TripRecorder.FLUSH_WAIT_MS + 1)
        runCurrent()
        // No location request while paused (CLAUDE.md rule 11).
        assertThat(source.listening).isEqualTo(0)
        assertThat(trips.trips.single().status).isEqualTo(TripStatus.Paused)

        recorder.resume()
        runCurrent()
        assertThat(source.listening).isEqualTo(1)
        // Drove 5 km while paused: not counted.
        send(listOf(fix(1_000, 12.950), fix(1_005, 12.951)))

        val saved = recorder.stop()
        assertThat(saved).isEqualTo(id)
        assertThat(source.listening).isEqualTo(0)
        assertThat(source.flushes).isEqualTo(2)
        assertThat(recorder.state.value).isEqualTo(RecordingState.Idle)
        val trip = trips.trips.single()
        assertThat(trip.status).isEqualTo(TripStatus.Done)
        assertThat(trip.source).isEqualTo(TripSource.Gps)
        assertThat(trip.distanceKm).isWithin(0.001).of(0.333)
        assertThat(trip.recordedSeconds).isEqualTo(15)
        assertThat(trip.estimatedFuelCost).isNotNull()
        // Raw points are gone; the simplified route stays.
        assertThat(trips.points(id)).isEmpty()
        assertThat(trips.route(id).map { it.segment }.toSet()).containsExactly(0, 1)
    }

    @Test
    fun `an accidental start is discarded`() = runTest {
        val recorder = recorder()
        recorder.start("safari")
        runCurrent()
        send(listOf(fix(0, 12.900), fix(5, 12.90001)))
        assertThat(recorder.stop()).isNull()
        assertThat(trips.trips).isEmpty()
    }

    @Test
    fun `an interrupted trip can be finished or resumed`() = runTest {
        val cut = Trip(
            id = "cut",
            vehicleId = "safari",
            startUtcMs = clock.millis(),
            zoneId = zone.id,
            distanceKm = 0.0,
            purpose = TripPurpose.Personal,
            source = TripSource.Gps,
            status = TripStatus.Recording,
        )
        val repo = FakeTripRepository(listOf(cut))
        repo.appendPoints("cut", listOf(fix(0, 12.900), fix(5, 12.901)))
        val recorder = recorder(repo)

        recorder.resumeInterrupted("cut")
        runCurrent()
        assertThat(source.listening).isEqualTo(1)
        send(listOf(fix(600, 12.910), fix(605, 12.911)))
        assertThat(repo.points("cut").last().segment).isEqualTo(1)
        recorder.stop()
        assertThat(repo.trips.single().distanceKm).isWithin(0.001).of(0.222)

        val other = cut.copy(id = "other")
        repo.save(other)
        repo.appendPoints("other", listOf(fix(0, 12.900), fix(5, 12.901), fix(10, 12.902)))
        assertThat(recorder.finishInterrupted("other")).isEqualTo("other")
        val finished = repo.observeTrip("other").first()!!
        assertThat(finished.status).isEqualTo(TripStatus.Done)
        // Ends at its last fix, not when the user tapped Finish.
        assertThat(finished.endUtcMs).isEqualTo(10_000L)
    }

    @Test
    fun `revoked permission stops recording and keeps the trip`() = runTest {
        val recorder = recorder()
        val id = recorder.start("safari")
        runCurrent()
        send(listOf(fix(0, 12.900)))
        source.revoke()
        runCurrent()
        assertThat(recorder.state.value).isEqualTo(RecordingState.Idle)
        assertThat(trips.observeTrip(id).first()?.status).isEqualTo(TripStatus.Interrupted)
        assertThat(trips.points(id)).hasSize(1)
    }

    private suspend fun TestScope.send(batch: List<TrackPoint>) {
        source.emit(batch)
        runCurrent()
    }

    private fun fix(seconds: Long, lat: Double) = TrackPoint(seconds * 1_000, lat, 77.6, 8f, null, 0)

    private class FakeSource : TripLocationSource {
        private val batches = MutableSharedFlow<List<TrackPoint>?>()
        var listening = 0
        var flushes = 0

        override val hasPermission = true

        override fun fixes(lifetimeMs: Long): Flow<List<TrackPoint>> = flow {
            listening++
            try {
                batches.collect { batch -> emit(batch ?: throw SecurityException("revoked")) }
            } finally {
                listening--
            }
        }

        override fun flush() {
            flushes++
        }

        suspend fun emit(batch: List<TrackPoint>) = batches.emit(batch)

        suspend fun revoke() = batches.emit(null)
    }
}

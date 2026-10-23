package app.fuelwise.core.location

import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.EstimateTripCost
import app.fuelwise.core.domain.trip.StillDetector
import app.fuelwise.core.domain.trip.TrackSummary
import app.fuelwise.core.domain.trip.keepAutoTrip
import app.fuelwise.core.domain.trip.simplifyTrack
import app.fuelwise.core.domain.trip.summarizeTrack
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What the recorder is doing, for the live screen, the notification and the Trips list. */
sealed interface RecordingState {
    data object Idle : RecordingState

    data class Active(
        val tripId: String,
        val vehicleId: String,
        val startUtcMs: Long,
        val paused: Boolean,
        val summary: TrackSummary,
        /** Simplified route so far, for the live trace. */
        val route: List<TrackPoint>,
        /** Recording time before the current stretch: earlier stretches, pauses left out. */
        val activeMsBefore: Long,
        /** When the current stretch began; null while paused. */
        val activeSinceUtcMs: Long?,
        /** [TripSource.Auto] when driving detection started it (PRD F-21.3); it then also ends on its own. */
        val source: TripSource = TripSource.Gps,
    ) : RecordingState {
        fun elapsedMs(nowUtcMs: Long): Long =
            activeMsBefore + (activeSinceUtcMs?.let { (nowUtcMs - it).coerceAtLeast(0) } ?: 0)
    }
}

/** Starts and stops the foreground service that keeps the process alive while recording. */
interface RecordingHost {
    fun startForegroundRecording()
}

/** The recorder's own scope: it outlives screens, and lives as long as the process. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecorderScope

/**
 * GPS trip recorder (PRD F-07.2, F-07.3). Each delivered batch is encrypted and written straight away, so a crash or
 * reboot loses at most the fixes Play services hadn't delivered yet. Outside a recording trip there is no location
 * request at all (CLAUDE.md rule 11): pausing or stopping cancels it.
 */
@Suppress("TooManyFunctions") // One recorder for manual GPS and automatic trips: start, pause, resume, stop, discard.
@Singleton
class TripRecorder @Inject constructor(
    private val trips: TripRepository,
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val estimateCost: EstimateTripCost,
    private val source: TripLocationSource,
    private val host: RecordingHost,
    private val prompts: TripPrompts,
    private val clock: Clock,
    private val zoneId: ZoneId,
    @param:RecorderScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    val hasPermission: Boolean get() = source.hasPermission

    private val lock = Mutex()
    private var points: MutableList<TrackPoint> = mutableListOf()
    private var segment = 0
    private var job: Job? = null

    // Automatic trips only (PRD F-21.3): "no movement > 50 m for 5 minutes" ends the trip.
    private var still: StillDetector? = null
    private var stillJob: Job? = null

    /**
     * Starts a new trip for [vehicleId], or returns the one already recording. [TripSource.Auto] is a trip driving
     * detection started (PRD F-21.3): it also ends by itself after standing still, and is kept only past the
     * automatic-trip minimums.
     */
    suspend fun start(vehicleId: String, source: TripSource = TripSource.Gps): String = lock.withLock {
        (state.value as? RecordingState.Active)?.let { return it.tripId }
        val now = clock.millis()
        val id = trips.save(
            Trip(
                id = "",
                vehicleId = vehicleId,
                startUtcMs = now,
                zoneId = zoneId.id,
                distanceKm = 0.0,
                purpose = TripPurpose.Personal,
                source = source,
                status = TripStatus.Recording,
            ),
        )
        points = mutableListOf()
        segment = 0
        _state.value =
            RecordingState.Active(id, vehicleId, now, false, TrackSummary.EMPTY, emptyList(), 0, now, source)
        host.startForegroundRecording()
        listen(id)
        if (source == TripSource.Auto) watchForStop(id, now)
        id
    }

    /** Picks up a trip a crash or reboot cut short (PRD F-07.3), in a new segment so the gap isn't counted. */
    suspend fun resumeInterrupted(tripId: String) = lock.withLock {
        if (state.value is RecordingState.Active) return@withLock
        val trip = trips.observeTrip(tripId).first() ?: return@withLock
        points = trips.points(tripId).toMutableList()
        segment = (points.maxOfOrNull { it.segment } ?: -1) + 1
        val summary = summarizeTrack(points)
        val now = clock.millis()
        trips.save(trip.copy(status = TripStatus.Recording))
        _state.value = RecordingState.Active(
            tripId,
            trip.vehicleId,
            trip.startUtcMs,
            false,
            summary,
            simplifyTrack(points),
            summary.recordedSeconds * MS_PER_S,
            now,
            trip.source,
        )
        host.startForegroundRecording()
        listen(tripId)
        if (trip.source == TripSource.Auto) watchForStop(tripId, now)
    }

    fun pause() {
        scope.launch {
            collectHeldFixes()
            lock.withLock {
                val active = state.value as? RecordingState.Active ?: return@withLock
                if (active.paused) return@withLock
                // Wait until the request is actually removed: zero requests while paused (CLAUDE.md rule 11).
                job?.cancelAndJoin()
                job = null
                setStatus(active.tripId, TripStatus.Paused)
                val now = clock.millis()
                _state.value =
                    active.copy(paused = true, activeMsBefore = active.elapsedMs(now), activeSinceUtcMs = null)
            }
        }
    }

    fun resume() {
        scope.launch {
            lock.withLock {
                val active = state.value as? RecordingState.Active ?: return@withLock
                if (!active.paused) return@withLock
                segment++
                setStatus(active.tripId, TripStatus.Recording)
                val now = clock.millis()
                _state.value = active.copy(paused = false, activeSinceUtcMs = now)
                listen(active.tripId)
                // Standing still while paused doesn't count towards ending the trip.
                if (still != null) still = StillDetector(now)
            }
        }
    }

    /**
     * Stops recording and saves the trip with its simplified route; the raw points are deleted. Returns the trip id,
     * or null when the trip was too short to keep ([MIN_KEEP_KM]) and was discarded.
     */
    suspend fun stop(onlyAutomatic: Boolean = false): String? {
        // Driving detection saw the user leave the vehicle (PRD F-21.3): that ends an automatic trip only. A trip
        // the user started themselves is theirs to stop.
        if (onlyAutomatic && (state.value as? RecordingState.Active)?.source != TripSource.Auto) return null
        collectHeldFixes()
        return lock.withLock { stopLocked(clock.millis()) }
    }

    /** "Not my drive" (PRD F-21.3): the user was a passenger. Throws the recording away; nothing is kept. */
    suspend fun discardCurrent() = lock.withLock {
        val active = state.value as? RecordingState.Active ?: return@withLock
        endJobs()
        trips.discardRecording(active.tripId)
        points = mutableListOf()
        _state.value = RecordingState.Idle
    }

    private suspend fun stopLocked(endUtcMs: Long): String? {
        val active = state.value as? RecordingState.Active ?: return null
        endJobs()
        val id = finish(active.tripId, endUtcMs, trips.points(active.tripId))
        points = mutableListOf()
        _state.value = RecordingState.Idle
        // Ask for the odometer while the drive is fresh (PRD F-07.6). Not after every automatic drive: that would
        // be a notification per journey, and the GPS estimate (F-21.5) covers it.
        if (id != null && active.source != TripSource.Auto) {
            prompts.odometerAfterTrip(id, trips.observeTrip(id).first()?.distanceKm ?: 0.0)
        }
        return id
    }

    private suspend fun endJobs() {
        job?.cancelAndJoin()
        job = null
        // Not joined: the stop may have come from this very job.
        stillJob?.cancel()
        stillJob = null
        still = null
    }

    /**
     * Ends an automatic trip once it has stood still for 5 minutes (PRD F-21.3). Fixes are batched, so when the
     * delivered ones say "still" the held batch is fetched first: the car may have moved since. The trip ends when
     * the car last moved, not 5 minutes later.
     */
    private fun watchForStop(tripId: String, startUtcMs: Long) {
        still = StillDetector(startUtcMs)
        stillJob?.cancel()
        stillJob = scope.launch {
            while (isActive) {
                delay(STILL_CHECK_MS)
                val active = state.value as? RecordingState.Active ?: return@launch
                if (active.tripId != tripId) return@launch
                if (active.paused || still?.isStill(clock.millis()) != true) continue
                collectHeldFixes()
                val detector = still ?: return@launch
                if (detector.isStill(clock.millis())) {
                    // A separate coroutine: stopping cancels this one.
                    scope.launch {
                        lock.withLock {
                            if ((state.value as? RecordingState.Active)?.tripId == tripId) {
                                stopLocked(detector.lastMovedUtcMs)
                            }
                        }
                    }
                    return@launch
                }
            }
        }
    }

    /** Ends a trip a crash or reboot cut short, at its last fix (PRD F-07.3). Null if it was too short to keep. */
    suspend fun finishInterrupted(tripId: String): String? = lock.withLock {
        if ((state.value as? RecordingState.Active)?.tripId == tripId) return@withLock null
        val recorded = trips.points(tripId)
        val trip = trips.observeTrip(tripId).first() ?: return@withLock null
        finish(tripId, recorded.maxOfOrNull { it.atUtcMs } ?: trip.startUtcMs, recorded)
    }

    /** Asks for held fixes now; the live screen calls this so its numbers don't wait for a full batch. */
    fun flush() {
        if ((state.value as? RecordingState.Active)?.paused == false) source.flush()
    }

    // Fixes wait in Play services' batch for up to 90 s; ask for them, and give the callback a moment to land.
    private suspend fun collectHeldFixes() {
        if ((state.value as? RecordingState.Active)?.paused != false) return
        source.flush()
        delay(FLUSH_WAIT_MS)
    }

    private fun listen(tripId: String) {
        job?.cancel()
        job = scope.launch {
            try {
                // Each request ends after its lifetime; a long trip simply asks again (CLAUDE.md rule 11).
                while (isActive) {
                    withTimeoutOrNull(REQUEST_LIFETIME_MS) {
                        source.fixes(REQUEST_LIFETIME_MS).collect { batch -> onBatch(tripId, batch) }
                    }
                }
            } catch (_: SecurityException) {
                // Location was revoked mid-trip: keep what was recorded and offer to resume or finish later.
                lock.withLock {
                    setStatus(tripId, TripStatus.Interrupted)
                    _state.value = RecordingState.Idle
                }
            }
        }
    }

    private suspend fun onBatch(tripId: String, batch: List<TrackPoint>) = lock.withLock {
        val active = state.value as? RecordingState.Active ?: return@withLock
        if (active.tripId != tripId || active.paused || batch.isEmpty()) return@withLock
        val stamped = batch.map { it.copy(segment = segment) }
        trips.appendPoints(tripId, stamped)
        points += stamped
        still?.add(stamped)
        _state.value = active.copy(summary = summarizeTrack(points), route = simplifyTrack(points))
    }

    private suspend fun finish(tripId: String, endUtcMs: Long, recorded: List<TrackPoint>): String? {
        val trip = trips.observeTrip(tripId).first() ?: return null
        val summary = summarizeTrack(recorded)
        val keep = if (trip.source == TripSource.Auto) {
            keepAutoTrip(summary, trip.startUtcMs, endUtcMs)
        } else {
            summary.distanceKm >= MIN_KEEP_KM
        }
        if (!keep) {
            trips.discardRecording(tripId)
            return null
        }
        val vehicle = vehicles.observeVehicles().first().firstOrNull { it.id == trip.vehicleId }
        val estimate = vehicle?.let { estimateCost(it, entries.observeAllRefuels().first(), summary.distanceKm) }
        trips.finishRecording(
            trip.copy(
                endUtcMs = endUtcMs,
                distanceKm = summary.distanceKm,
                recordedSeconds = summary.recordedSeconds,
                idleSeconds = summary.idleSeconds,
                maxSpeedKmh = summary.maxSpeedKmh,
                estimatedFuelCost = estimate?.fuelCost,
                status = TripStatus.Done,
            ),
            simplifyTrack(recorded),
        )
        return tripId
    }

    private suspend fun setStatus(tripId: String, status: TripStatus) {
        trips.observeTrip(tripId).first()?.let { trips.save(it.copy(status = status)) }
    }

    companion object {
        /** Shorter than this isn't a trip: an accidental start. */
        const val MIN_KEEP_KM = 0.05
        const val REQUEST_LIFETIME_MS = 4 * 60 * 60 * 1_000L
        const val FLUSH_WAIT_MS = 2_000L

        /** How often an automatic trip checks whether it has stood still long enough to end. */
        const val STILL_CHECK_MS = 60_000L
        private const val MS_PER_S = 1_000L
    }
}

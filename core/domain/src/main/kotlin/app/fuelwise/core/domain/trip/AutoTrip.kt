package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

// Automatic trips (PRD F-21.3): the rules the recorder applies on its own, with no screen to ask the user.
// Kept here, pure, so they can be tested without a phone.

/**
 * "No movement > 50 m for 5 minutes" ends an automatic trip (PRD F-21.3). The anchor is the last place the car was
 * seen; a fix more than [STILL_RADIUS_M] from it moves the anchor and resets the clock. Fixes are deliberately sparse
 * while standing still (the request has a 25 m minimum distance), so the caller also asks [isStill] on a timer.
 */
class StillDetector(startUtcMs: Long) {
    private var anchor: TrackPoint? = null
    private var movedAtUtcMs: Long = startUtcMs

    fun add(points: List<TrackPoint>) {
        points.filter { it.accuracyM <= MAX_ACCURACY_M }.sortedBy { it.atUtcMs }.forEach { p ->
            val a = anchor
            if (a == null || distanceM(a.lat, a.lng, p.lat, p.lng) > STILL_RADIUS_M) {
                anchor = p
                movedAtUtcMs = maxOf(movedAtUtcMs, p.atUtcMs)
            }
        }
    }

    fun isStill(nowUtcMs: Long): Boolean = nowUtcMs - movedAtUtcMs >= STILL_FOR_MS

    /** When the car last moved: an automatic trip that ends by standing still ends here, not 5 minutes later. */
    val lastMovedUtcMs: Long get() = movedAtUtcMs
}

/** Automatic trips shorter than 500 m or 2 minutes are discarded (PRD F-21.3): a car moved in a car park. */
fun keepAutoTrip(summary: TrackSummary, startUtcMs: Long, endUtcMs: Long): Boolean =
    summary.distanceKm >= AUTO_MIN_KM && endUtcMs - startUtcMs >= AUTO_MIN_MS

/**
 * Which vehicle an automatic trip belongs to (PRD F-21.3): the car whose Bluetooth connected; otherwise the vehicle
 * the user usually drives at this time of day, learned from their own trips; otherwise [defaultVehicleId].
 * Two-wheeler and car trips are never told apart from speed.
 */
fun assignAutoTripVehicle(
    bluetoothVehicleId: String?,
    start: ZonedDateTime,
    history: List<Trip>,
    activeVehicleIds: Set<String>,
    defaultVehicleId: String?,
): String? {
    bluetoothVehicleId?.takeIf { it in activeVehicleIds }?.let { return it }
    habitualVehicle(start, history, activeVehicleIds)?.let { return it }
    return defaultVehicleId?.takeIf { it in activeVehicleIds } ?: activeVehicleIds.firstOrNull()
}

/**
 * The vehicle with most of the trips that started within [HABIT_WINDOW_HOURS] of this time of day, on the same kind
 * of day (weekday or weekend). Needs [HABIT_MIN_TRIPS] such trips and a clear majority, or it doesn't guess.
 * Trips the user recorded or corrected count; that's how a correction teaches it.
 */
internal fun habitualVehicle(now: ZonedDateTime, history: List<Trip>, activeVehicleIds: Set<String>): String? {
    val similar = history.filter { trip ->
        if (trip.vehicleId !in activeVehicleIds || trip.source == TripSource.Manual) return@filter false
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(trip.startUtcMs), now.zone)
        t.isWeekend() == now.isWeekend() && minutesApart(t, now) <= HABIT_WINDOW_HOURS * MINUTES_PER_HOUR
    }
    if (similar.size < HABIT_MIN_TRIPS) return null
    val (vehicleId, count) = similar.groupingBy { it.vehicleId }.eachCount().maxBy { it.value }
    return vehicleId.takeIf { count * 2 > similar.size }
}

private fun ZonedDateTime.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

/** Minutes between two times of day, the short way round midnight. */
private fun minutesApart(a: ZonedDateTime, b: ZonedDateTime): Int {
    val d = abs((a.hour * MINUTES_PER_HOUR + a.minute) - (b.hour * MINUTES_PER_HOUR + b.minute))
    return minOf(d, MINUTES_PER_DAY - d)
}

/** "Pause automatic trips" choices (PRD F-21.3 controls). */
enum class AutoTripPause { OneHour, UntilTomorrow, UntilTurnedOn }

/**
 * When a pause chosen at [nowUtcMs] ends, in UTC ms. "Until tomorrow" ends at [TOMORROW_RESUME_HOUR] local time
 * tomorrow, so an early drive isn't missed; "until turned on" never ends by itself ([Long.MAX_VALUE]).
 */
fun autoTripPauseEnd(pause: AutoTripPause, nowUtcMs: Long, zone: ZoneId): Long = when (pause) {
    AutoTripPause.OneHour -> nowUtcMs + MS_PER_HOUR

    AutoTripPause.UntilTomorrow ->
        Instant.ofEpochMilli(nowUtcMs).atZone(zone).toLocalDate().plusDays(1)
            .atTime(TOMORROW_RESUME_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    AutoTripPause.UntilTurnedOn -> Long.MAX_VALUE
}

const val STILL_RADIUS_M = 50.0
const val STILL_FOR_MS = 5 * 60 * 1_000L
const val AUTO_MIN_KM = 0.5
const val AUTO_MIN_MS = 2 * 60 * 1_000L
const val HABIT_WINDOW_HOURS = 1
const val HABIT_MIN_TRIPS = 3
const val TOMORROW_RESUME_HOUR = 5

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val MS_PER_HOUR = 60 * 60 * 1_000L

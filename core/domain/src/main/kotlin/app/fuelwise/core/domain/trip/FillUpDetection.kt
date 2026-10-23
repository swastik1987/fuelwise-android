package app.fuelwise.core.domain.trip

import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.station.StationFuel
import app.fuelwise.core.domain.station.sells
import app.fuelwise.core.model.TrackPoint

// Fill-up detection (PRD F-21.4): pure rules. The trip recorder hands over a finished trip's fixes; this finds where
// the car stood and decides whether one of those stops deserves a "Filled up at …? Tap to log." prompt.

/** A place the car stood still. [endUtcMs] is null for the trip's end, whose length isn't known. */
data class Stop(val lat: Double, val lng: Double, val startUtcMs: Long, val endUtcMs: Long?) {
    val durationMs: Long? get() = endUtcMs?.let { it - startUtcMs }
}

/**
 * Stops in a track: runs of fixes within [radiusM] of where the car first stood, lasting [MIN_STOP_MS]–[MAX_STOP_MS].
 * Fixes are sparse while standing still (25 m minimum distance), so a stop shows as a gap: it lasts until the first
 * fix away from it. The trip's end is always a candidate, with no length, since the car stood there when it ended.
 */
fun findStops(points: List<TrackPoint>, radiusM: Double = STATION_RADIUS_M): List<Stop> {
    val fixes = points.filter { it.accuracyM <= MAX_ACCURACY_M }.sortedBy { it.atUtcMs }
    if (fixes.isEmpty()) return emptyList()
    val stops = mutableListOf<Stop>()
    var i = 0
    while (i < fixes.size) {
        val anchor = fixes[i]
        var j = i + 1
        while (j < fixes.size && distanceM(anchor.lat, anchor.lng, fixes[j].lat, fixes[j].lng) <= radiusM) j++
        if (j == fixes.size) {
            stops += Stop(anchor.lat, anchor.lng, anchor.atUtcMs, endUtcMs = null)
        } else {
            val next = fixes[j]
            val length = next.atUtcMs - anchor.atUtcMs
            // A gap alone isn't a stop: a tunnel or a lost signal looks the same. It is when the car pulled away
            // (a second fix nearby) or the hop to the next fix is walking pace at most.
            val pulledAway = j - i >= 2
            val slowHop = distanceM(anchor.lat, anchor.lng, next.lat, next.lng) / (length / MS_PER_S) <= STOP_SPEED_MPS
            if (length in MIN_STOP_MS..MAX_STOP_MS && (pulledAway || slowHop)) {
                stops += Stop(anchor.lat, anchor.lng, anchor.atUtcMs, next.atUtcMs)
            }
        }
        i = j
    }
    return stops
}

/** What a vehicle fills with, for station fit: an EV only charges; a CNG car (often bi-fuel) prefers CNG pumps. */
enum class FillNeed { Liquid, Cng, Electric }

/**
 * The station a stop was most likely a fill-up at: the nearest one within [STATION_RADIUS_M] that fits [need], and that
 * the user hasn't rejected [MAX_REJECTIONS] times ("Not a fill-up", PRD F-21.4 learning). Null if none.
 */
fun stationForStop(near: List<Pair<Station, Double>>, need: FillNeed, rejections: Map<Long, Int>): Station? {
    val candidates = near.filter { (s, d) ->
        d <= STATION_RADIUS_M && (rejections[s.osmId] ?: 0) < MAX_REJECTIONS &&
            when (need) {
                FillNeed.Electric -> s.charging
                FillNeed.Liquid, FillNeed.Cng -> !s.charging
            }
    }
    // A CNG car at a pump pair across the road: the one selling CNG wins over a nearer petrol-only one.
    val fitting = if (need == FillNeed.Cng) candidates.filter { it.first.sells(StationFuel.Cng) } else emptyList()
    return (fitting.ifEmpty { candidates }).minByOrNull { it.second }?.first
}

/**
 * Whether to prompt for a stop at [stopUtcMs] (PRD F-21.4 guards): not when a fill-up for the vehicle was already
 * logged within ±2 hours, and at most one prompt per vehicle per 6 hours.
 */
fun shouldPromptFillUp(
    stopUtcMs: Long,
    loggedFillUpsUtcMs: List<Long>,
    lastPromptUtcMs: Long?,
    nowUtcMs: Long,
): Boolean {
    if (loggedFillUpsUtcMs.any { kotlin.math.abs(it - stopUtcMs) <= ALREADY_LOGGED_MS }) return false
    return lastPromptUtcMs == null || nowUtcMs - lastPromptUtcMs >= PROMPT_GAP_MS
}

const val STATION_RADIUS_M = 40.0
const val STOP_SPEED_MPS = 1.0
const val MIN_STOP_MS = 2 * 60 * 1_000L
const val MAX_STOP_MS = 15 * 60 * 1_000L
const val MAX_REJECTIONS = 3
const val ALREADY_LOGGED_MS = 2 * 60 * 60 * 1_000L
const val PROMPT_GAP_MS = 6 * 60 * 60 * 1_000L

private const val MS_PER_S = 1_000.0

/**
 * What a fill-up prompt pre-fills in the Add fill-up sheet (PRD F-21.4): station, time and vehicle. The odometer is
 * left to the sheet's own hint (the GPS estimate, F-21.5), never filled as a value. Travels as one string through the
 * notification tap.
 */
data class FillUpPrefill(val vehicleId: String, val atUtcMs: Long, val station: String?) {
    fun encode(): String = listOf(VERSION, vehicleId, atUtcMs.toString(), station.orEmpty().replace('\t', ' '))
        .joinToString("\t")

    companion object {
        private const val VERSION = "1"
        private const val FIELDS = 4
        private const val V = 0
        private const val VEHICLE = 1
        private const val AT = 2
        private const val STATION = 3

        fun decode(text: String?): FillUpPrefill? {
            val parts = text?.split('\t')?.takeIf { it.size == FIELDS && it[V] == VERSION } ?: return null
            val at = parts[AT].toLongOrNull() ?: return null
            return FillUpPrefill(parts[VEHICLE], at, parts[STATION].ifEmpty { null })
        }
    }
}

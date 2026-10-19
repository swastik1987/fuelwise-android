package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint
import java.nio.ByteBuffer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** What a recorded track adds up to (PRD F-07.2: distance, duration, average/max speed, idle time). */
data class TrackSummary(
    val distanceKm: Double,
    /** Time between fixes inside segments: pauses aren't counted. */
    val recordedSeconds: Long,
    val idleSeconds: Long,
    val maxSpeedKmh: Double?,
) {
    val averageKmh: Double? get() = if (recordedSeconds > 0) distanceKm / (recordedSeconds / SECONDS_PER_HOUR) else null

    companion object {
        val EMPTY = TrackSummary(0.0, 0, 0, null)
    }
}

/**
 * Sums a track. Fixes worse than [MAX_ACCURACY_M] are dropped, and so is a fix that would need more than
 * [MAX_SPEED_KMH] to reach (a GPS jump). Time moving slower than [IDLE_BELOW_KMH] counts as idle.
 * A new segment (after a pause) starts fresh: the gap is neither distance nor time.
 */
fun summarizeTrack(points: List<TrackPoint>): TrackSummary =
    TrackAccumulator().apply { usable(points).forEach(::add) }.summary()

/** Running totals for [summarizeTrack]. */
private class TrackAccumulator {
    private var distanceM = 0.0
    private var recordedMs = 0L
    private var idleMs = 0L
    private var maxReported: Double? = null
    private var maxImplied: Double? = null
    private var previous: TrackPoint? = null

    fun add(p: TrackPoint) {
        val reported = p.speedMps?.let { it * MPS_TO_KMH }?.takeIf { it <= MAX_SPEED_KMH }
        if (reported != null) maxReported = max(maxReported ?: 0.0, reported)
        val prev = previous
        if (prev == null || prev.segment != p.segment) {
            previous = p
            return
        }
        val dtMs = p.atUtcMs - prev.atUtcMs
        if (dtMs <= 0) return
        val d = distanceM(prev.lat, prev.lng, p.lat, p.lng)
        val kmh = d / (dtMs / MS_PER_S) * MPS_TO_KMH
        // A jump: keep the previous fix and judge the next one against it.
        if (kmh > MAX_SPEED_KMH) return
        distanceM += d
        recordedMs += dtMs
        if (kmh < IDLE_BELOW_KMH) idleMs += dtMs
        if (dtMs >= MIN_MS_FOR_IMPLIED_SPEED) maxImplied = max(maxImplied ?: 0.0, kmh)
        previous = p
    }

    fun summary() = TrackSummary(
        distanceKm = distanceM / M_PER_KM,
        recordedSeconds = recordedMs / MS_PER_S.toLong(),
        idleSeconds = idleMs / MS_PER_S.toLong(),
        // The phone's own speed is better than one worked out from two fixes; fall back when it isn't reported.
        maxSpeedKmh = maxReported ?: maxImplied,
    )
}

/**
 * Douglas-Peucker on each segment, [toleranceM] metres off the line (PRD F-07.2: ~5 m). Distances are measured on a
 * local flat projection, which is accurate to well under a metre at trip scale.
 */
fun simplifyTrack(points: List<TrackPoint>, toleranceM: Double = ROUTE_TOLERANCE_M): List<TrackPoint> =
    usable(points).groupBy { it.segment }.toSortedMap().values.flatMap { simplifySegment(it, toleranceM) }

/** Great-circle distance in metres. */
fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private fun usable(points: List<TrackPoint>) = points.filter { it.accuracyM <= MAX_ACCURACY_M }.sortedBy { it.atUtcMs }

private fun simplifySegment(points: List<TrackPoint>, toleranceM: Double): List<TrackPoint> {
    // Two points or fewer: nothing between the ends to drop.
    if (points.size <= 2) return points
    val lat0 = Math.toRadians(points.first().lat)
    val xs = DoubleArray(points.size) { Math.toRadians(points[it].lng) * cos(lat0) * EARTH_RADIUS_M }
    val ys = DoubleArray(points.size) { Math.toRadians(points[it].lat) * EARTH_RADIUS_M }
    val keep = BooleanArray(points.size).apply {
        this[0] = true
        this[lastIndex] = true
    }
    // Iterative, so a long trip can't overflow the stack.
    val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.lastIndex) }
    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast()
        var farthest = -1
        var farthestD = toleranceM
        for (i in start + 1 until end) {
            val d = offLine(xs, ys, i, start, end)
            if (d > farthestD) {
                farthest = i
                farthestD = d
            }
        }
        if (farthest >= 0) {
            keep[farthest] = true
            stack.add(start to farthest)
            stack.add(farthest to end)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

/** Distance from point [p] to the segment from [a] to [b], all indexes into the projected [xs], [ys]. */
private fun offLine(xs: DoubleArray, ys: DoubleArray, p: Int, a: Int, b: Int): Double {
    val px = xs[p]
    val py = ys[p]
    val ax = xs[a]
    val ay = ys[a]
    val bx = xs[b]
    val by = ys[b]
    val dx = bx - ax
    val dy = by - ay
    val lengthSq = dx * dx + dy * dy
    if (lengthSq == 0.0) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/**
 * Compact binary form of a track, for encrypted point batches and stored routes. Version 1: count, then per point
 * time (long), lat, lng (double), accuracy, speed (float, NaN = none) and segment (int).
 */
object TrackCodec {
    private const val VERSION: Byte = 1
    private const val POINT_BYTES = Long.SIZE_BYTES + 2 * Double.SIZE_BYTES + 2 * Float.SIZE_BYTES + Int.SIZE_BYTES

    fun encode(points: List<TrackPoint>): ByteArray {
        val buffer = ByteBuffer.allocate(1 + Int.SIZE_BYTES + points.size * POINT_BYTES)
        buffer.put(VERSION).putInt(points.size)
        points.forEach { p ->
            buffer.putLong(p.atUtcMs).putDouble(p.lat).putDouble(p.lng).putFloat(p.accuracyM)
                .putFloat(p.speedMps ?: Float.NaN).putInt(p.segment)
        }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): List<TrackPoint> {
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.get() == VERSION) { "Unknown track format" }
        return List(buffer.int) {
            TrackPoint(
                atUtcMs = buffer.long,
                lat = buffer.double,
                lng = buffer.double,
                accuracyM = buffer.float,
                speedMps = buffer.float.takeUnless { it.isNaN() },
                segment = buffer.int,
            )
        }
    }
}

const val MAX_ACCURACY_M = 50f
const val MAX_SPEED_KMH = 250.0
const val IDLE_BELOW_KMH = 5.0
const val ROUTE_TOLERANCE_M = 5.0

private const val EARTH_RADIUS_M = 6_371_008.8
private const val MPS_TO_KMH = 3.6
private const val MS_PER_S = 1_000.0
private const val M_PER_KM = 1_000.0
private const val SECONDS_PER_HOUR = 3_600.0
private const val MIN_MS_FOR_IMPLIED_SPEED = 4_000L

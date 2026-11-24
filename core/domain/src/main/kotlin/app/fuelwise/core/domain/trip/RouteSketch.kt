package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.Money
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * A recorded route reduced to a few points in a unit square (x right, y down, both 0..1, aspect kept and centred), for
 * the thumbnail on a Trips card (owner choice 24 Nov 2026, option B). Drawn on the phone from the saved route; no map
 * tiles and no network.
 */
data class RouteSketch(val xs: FloatArray, val ys: FloatArray) {
    val size: Int get() = xs.size

    override fun equals(other: Any?): Boolean =
        other is RouteSketch && xs.contentEquals(other.xs) && ys.contentEquals(other.ys)

    override fun hashCode(): Int = 31 * xs.contentHashCode() + ys.contentHashCode()

    companion object {
        /** Enough to read the shape at thumbnail size, cheap to draw in a list. */
        const val MAX_POINTS = 48

        /**
         * Equirectangular projection around the route's mean latitude, so east-west distances aren't stretched.
         * Null when there's nothing to draw: fewer than two points, or every point in the same place.
         */
        fun of(points: List<TrackPoint>, maxPoints: Int = MAX_POINTS): RouteSketch? {
            if (points.size < 2) return null
            val step = max(1, (points.size + maxPoints - 1) / maxPoints)
            val kept = points.filterIndexed { i, _ -> i % step == 0 }.let {
                if (it.last() === points.last()) it else it + points.last()
            }
            val shrink = cos(Math.toRadians(kept.sumOf { it.lat } / kept.size))
            val x = kept.map { it.lng * shrink }
            val y = kept.map { -it.lat }
            val minX = x.min()
            val minY = y.min()
            val width = x.max() - minX
            val height = y.max() - minY
            val span = max(width, height)
            if (span <= 0.0) return null
            val padX = (span - width) / 2
            val padY = (span - height) / 2
            return RouteSketch(
                FloatArray(kept.size) { ((x[it] - minX + padX) / span).toFloat() },
                FloatArray(kept.size) { ((y[it] - minY + padY) / span).toFloat() },
            )
        }
    }
}

/** Average speed over the time driven: recorded time for GPS trips (pauses left out), else start to end. */
fun averageSpeedKmh(trip: Trip): Double? {
    val seconds = trip.recordedSeconds ?: trip.endUtcMs?.let { (it - trip.startUtcMs) / MILLIS_PER_SECOND }
    return seconds?.takeIf { it > 0 && trip.distanceKm > 0 }?.let { trip.distanceKm / (it / SECONDS_PER_HOUR) }
}

/** Cost per km of a trip's estimated cost (fuel plus linked tolls and parking). */
fun costPerKm(cost: Money?, distanceKm: Double): Money? =
    cost?.takeIf { distanceKm > 0 }?.let { Money((it.paise / distanceKm).roundToLong()) }

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_HOUR = 3_600.0

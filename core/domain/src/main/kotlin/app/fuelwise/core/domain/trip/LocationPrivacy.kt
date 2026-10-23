package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint

// Location privacy and retention (PRD F-21.6): pure rules, applied by the data layer.

/** How long saved routes are kept before only the trip summary remains. 90 days by default. */
@Suppress("MagicNumber") // The day counts are the choices themselves (PRD F-21.6).
enum class RouteRetention(val days: Int?) {
    Days30(30),
    Days90(90),
    Year(365),
    Forever(null),
    ;

    /** Routes of trips that ended before this UTC ms are removed; null = never. */
    fun cutoffUtcMs(nowUtcMs: Long): Long? = days?.let { nowUtcMs - it * MS_PER_DAY }

    companion object {
        val DEFAULT = Days90
    }
}

/** A spot the user never wants recorded, e.g. home (PRD F-21.6). Coordinates stay on the phone, encrypted. */
data class PrivatePlace(val id: String, val name: String, val lat: Double, val lng: Double)

/**
 * Trims a route's ends inside any private place ([radiusM], 200 m), so the start and end of a shared map or GPX don't
 * reveal an exact address. Only the ends: a drive that passes near home mid-way keeps that stretch. A route that never
 * leaves a private place comes back empty.
 */
fun trimPrivateEnds(
    route: List<TrackPoint>,
    places: List<PrivatePlace>,
    radiusM: Double = PRIVATE_RADIUS_M,
): List<TrackPoint> {
    if (places.isEmpty() || route.isEmpty()) return route
    fun private(p: TrackPoint) = places.any { distanceM(it.lat, it.lng, p.lat, p.lng) <= radiusM }
    val first = route.indexOfFirst { !private(it) }
    if (first < 0) return emptyList()
    val last = route.indexOfLast { !private(it) }
    return route.subList(first, last + 1)
}

const val PRIVATE_RADIUS_M = 200.0

private const val MS_PER_DAY = 24L * 60 * 60 * 1_000

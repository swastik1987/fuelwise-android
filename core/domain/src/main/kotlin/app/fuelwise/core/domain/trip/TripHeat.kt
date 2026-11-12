package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint
import kotlin.math.ceil

// The consolidated trips map (owner decision 12 Nov 2026, user feedback; PRD F-07.7, free in R1): every saved route
// as a heatmap, plus the places the user goes often. Pure; drawn by core:ui.

/** A point on the heatmap. */
data class HeatPoint(val lat: Double, val lng: Double)

/** A place trips start or end at, [visits] times. */
data class VisitedPlace(val lat: Double, val lng: Double, val visits: Int)

/**
 * Evenly spaced points along a saved route, about [stepM] apart, so a long straight stretch (few points after
 * simplification) is as hot as a winding one. A pause ([TrackPoint.segment]) isn't bridged.
 */
fun densifyRoute(route: List<TrackPoint>, stepM: Double = HEAT_STEP_M): List<HeatPoint> {
    if (route.isEmpty()) return emptyList()
    val out = mutableListOf(HeatPoint(route.first().lat, route.first().lng))
    for (i in 1 until route.size) {
        val a = route[i - 1]
        val b = route[i]
        if (a.segment == b.segment) {
            val steps = ceil(distanceM(a.lat, a.lng, b.lat, b.lng) / stepM).toInt().coerceAtLeast(1)
            for (s in 1 until steps) {
                val t = s.toDouble() / steps
                out += HeatPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
            }
        }
        out += HeatPoint(b.lat, b.lng)
    }
    return out
}

/** At most [max] points, keeping every n-th, so years of routes still draw quickly. */
fun capHeat(points: List<HeatPoint>, max: Int = MAX_HEAT_POINTS): List<HeatPoint> {
    if (points.size <= max) return points
    val every = ceil(points.size.toDouble() / max).toInt()
    return points.filterIndexed { i, _ -> i % every == 0 }
}

/**
 * Trip starts and ends grouped into places within [radiusM] of each other; those visited at least [minVisits] times,
 * busiest first. Each place sits at the average of its visits.
 */
fun frequentPlaces(
    ends: List<HeatPoint>,
    minVisits: Int = MIN_VISITS,
    radiusM: Double = PLACE_RADIUS_M,
): List<VisitedPlace> {
    val groups = mutableListOf<MutableList<HeatPoint>>()
    ends.forEach { p ->
        val home = groups.firstOrNull { g ->
            val c = g.centre()
            distanceM(c.lat, c.lng, p.lat, p.lng) <= radiusM
        }
        if (home != null) home += p else groups += mutableListOf(p)
    }
    return groups.filter { it.size >= minVisits }
        .map { g -> g.centre().let { VisitedPlace(it.lat, it.lng, g.size) } }
        .sortedByDescending { it.visits }
}

private fun List<HeatPoint>.centre() = HeatPoint(sumOf { it.lat } / size, sumOf { it.lng } / size)

const val HEAT_STEP_M = 50.0
const val MAX_HEAT_POINTS = 20_000
private const val MIN_VISITS = 2
private const val PLACE_RADIUS_M = 150.0

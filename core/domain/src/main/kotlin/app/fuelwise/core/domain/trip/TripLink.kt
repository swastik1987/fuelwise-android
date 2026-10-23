package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripStatus
import kotlin.math.abs

/**
 * Trips an expense or toll can be linked to (PRD F-04.1, F-10.2): the vehicle's finished trips within
 * [windowDays] of [atUtcMs]. A trip under way at that time comes first, then the nearest in time. The user picks one;
 * nothing is linked for them.
 */
fun tripsToLink(trips: List<Trip>, vehicleId: String, atUtcMs: Long, windowDays: Int = 7, limit: Int = 4): List<Trip> {
    val window = windowDays * DAY_MS
    return trips
        .asSequence()
        .filter { it.vehicleId == vehicleId && it.status == TripStatus.Done }
        .filter { abs(it.gapTo(atUtcMs)) <= window }
        .sortedWith(compareBy({ it.gapTo(atUtcMs) != 0L }, { abs(it.gapTo(atUtcMs)) }))
        .take(limit)
        .toList()
}

/** 0 while the trip is under way (start to end, or the start day for a manual trip), else ms away from it. */
private fun Trip.gapTo(atUtcMs: Long): Long {
    val end = endUtcMs ?: (startUtcMs + DAY_MS)
    return when {
        atUtcMs < startUtcMs -> startUtcMs - atUtcMs
        atUtcMs > end -> atUtcMs - end
        else -> 0L
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

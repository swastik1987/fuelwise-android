package app.fuelwise.core.domain.station

import app.fuelwise.core.domain.trip.FillNeed
import app.fuelwise.core.domain.trip.distanceM
import app.fuelwise.core.model.Refuel
import java.util.Locale

/**
 * A station offered on the Add fuel sheet (PRD F-21.1). [lat]/[lng] are the station's position: the bundled list's
 * for an OpenStreetMap station, or where the user logged it before for one of their own. [distanceM] is from the
 * phone's current location.
 */
data class StationOption(
    val label: String,
    val lat: Double,
    val lng: Double,
    val distanceM: Double,
    /** The user has logged a fill-up here before, under this name. */
    val usedBefore: Boolean,
)

/**
 * Station auto-detect (PRD F-21.1), offline on the phone (CLAUDE.md rule 5). Ranks the bundled OpenStreetMap stations
 * and the user's own past stations near the current location:
 * 1. stations the user has used before (a strong boost, and their name for a spot replaces the list's),
 * 2. distance,
 * 3. fuel fit: a CNG vehicle towards pumps tagged with CNG, an EV to charging points only.
 *
 * Looks within 150 m first and widens to 300 m if nothing is found. Up to [MAX_OPTIONS] are returned, best first: GPS
 * in dense streets is 20–50 m off and pumps often face each other across a road, so alternatives are always shown.
 *
 * The user's own stations come from their saved fill-ups that have a station name and coordinates. Correcting the
 * station on a fill-up therefore teaches the next detection: no separate store is needed.
 */
object StationMatch {
    const val MAX_OPTIONS = 3
    const val NEAR_M = 150.0
    const val WIDE_M = 300.0

    /** [listed] is the bundled list within [WIDE_M] of the point (StationRepository.near), with distances. */
    fun options(
        lat: Double,
        lng: Double,
        listed: List<Pair<Station, Double>>,
        history: List<Refuel>,
        need: FillNeed,
    ): List<StationOption> {
        val query = Query(lat, lng, listed, history, need)
        return query.within(NEAR_M).ifEmpty { query.within(WIDE_M) }
    }

    private class Query(
        val lat: Double,
        val lng: Double,
        val listed: List<Pair<Station, Double>>,
        val history: List<Refuel>,
        val need: FillNeed,
    )

    private fun Query.within(radiusM: Double): List<StationOption> {
        val own = ownStations(lat, lng, history, radiusM)
        val fromList = listed.asSequence()
            .filter { (station, distance) -> distance <= radiusM && fits(station, need) }
            .mapNotNull { (station, distance) -> station.label?.let { Scored(station, it, distance) } }
            // The user's own name for a spot wins over the list's (PRD F-21.1 learning).
            .filterNot { s -> own.any { it.option.label.same(s.label) || it.closeTo(s.station) } }
            .map { s ->
                val misfit = if (need == FillNeed.Cng && s.station.fuels.isNotEmpty() &&
                    !s.station.sells(StationFuel.Cng)
                ) {
                    MISFIT_PENALTY_M
                } else {
                    0.0
                }
                Ranked(StationOption(s.label, s.station.lat, s.station.lng, s.distance, usedBefore = false), misfit)
            }
            .toList()
        return (own + fromList)
            .sortedBy { it.option.distanceM + it.penalty }
            .distinctBy { it.option.label.key() }
            .take(MAX_OPTIONS)
            .map { it.option }
    }

    /** One option per station name the user logged within [radiusM], at the nearest place they logged it. */
    private fun ownStations(lat: Double, lng: Double, history: List<Refuel>, radiusM: Double): List<Ranked> =
        history.asSequence()
            .mapNotNull { refuel ->
                val name = refuel.stationName?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                val at = refuel.lat ?: return@mapNotNull null
                val on = refuel.lng ?: return@mapNotNull null
                StationOption(name, at, on, distanceM(lat, lng, at, on), usedBefore = true)
            }
            .filter { it.distanceM <= radiusM }
            .groupBy { it.label.key() }
            .map { (_, visits) -> Ranked(visits.minBy { it.distanceM }, -USED_BEFORE_BOOST_M) }
            .toList()

    private fun fits(station: Station, need: FillNeed): Boolean = when (need) {
        FillNeed.Electric -> station.charging

        // A charging-only site is no use for a tank.
        FillNeed.Liquid, FillNeed.Cng -> station.fuels.isNotEmpty() || !station.charging
    }

    private fun Ranked.closeTo(station: Station): Boolean =
        distanceM(option.lat, option.lng, station.lat, station.lng) <= SAME_SPOT_M

    private fun String.key(): String = trim().lowercase(Locale.ROOT)

    private fun String.same(other: String): Boolean = key() == other.key()

    private class Scored(val station: Station, val label: String, val distance: Double)

    private class Ranked(val option: StationOption, val penalty: Double)

    /** A station used before is worth this many metres of distance. */
    private const val USED_BEFORE_BOOST_M = 150.0

    /** A CNG vehicle at a pump whose OSM tags say it has no CNG. Untagged pumps aren't penalised. */
    private const val MISFIT_PENALTY_M = 100.0

    /** A past fill-up this close to a listed station was at that station, whatever the user called it. */
    private const val SAME_SPOT_M = 40.0
}

/** Back-dated entries don't use the current location (PRD F-21.1): logged more than 30 minutes after the fill. */
fun isBackDated(entryUtcMs: Long, nowUtcMs: Long): Boolean = nowUtcMs - entryUtcMs > BACK_DATED_MS

private const val BACK_DATED_MS = 30 * 60 * 1_000L

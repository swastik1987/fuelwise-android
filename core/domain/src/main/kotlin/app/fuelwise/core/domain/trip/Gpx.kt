package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** One trip in a GPX file: a name and its saved (simplified) route. */
data class GpxTrack(val name: String, val points: List<TrackPoint>)

/**
 * GPX 1.1 export (PRD F-07.8): one `<trk>` per trip, one `<trkseg>` per recorded stretch (a pause starts a new
 * segment). Times are UTC. Written without any XML library so `core:domain` stays pure Kotlin.
 */
object GpxWriter {
    fun write(tracks: List<GpxTrack>, creator: String = CREATOR): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="${escape(creator)}" xmlns="http://www.topografix.com/GPX/1/1">""")
            .append('\n')
        tracks.filter { it.points.isNotEmpty() }.forEach { track ->
            append("  <trk>\n")
            append("    <name>").append(escape(track.name)).append("</name>\n")
            track.points.groupBy { it.segment }.toSortedMap().values.forEach { segment ->
                append("    <trkseg>\n")
                segment.sortedBy { it.atUtcMs }.forEach { p ->
                    append("      <trkpt lat=\"").append(coordinate(p.lat))
                        .append("\" lon=\"").append(coordinate(p.lng)).append("\">")
                    append("<time>").append(Instant.ofEpochMilli(p.atUtcMs)).append("</time></trkpt>\n")
                }
                append("    </trkseg>\n")
            }
            append("  </trk>\n")
        }
        append("</gpx>\n")
    }

    // Seven decimals is about 1 cm: more than GPS can tell. Locale.ROOT keeps the dot as decimal separator.
    private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.7f", value)

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private const val CREATOR = "Fuelwise"
}

/** Periods for exporting several trips at once (PRD F-07.8: per trip or range). */
enum class ExportRange { ThisWeek, ThisMonth, LastThreeMonths, All }

/** Start of [this] range in local time, as UTC ms; null for all time. Weeks start on Monday, as in the Trips list. */
fun ExportRange.startUtcMs(nowUtcMs: Long, zone: ZoneId): Long? {
    val today = Instant.ofEpochMilli(nowUtcMs).atZone(zone).toLocalDate()
    val start = when (this) {
        ExportRange.ThisWeek -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ExportRange.ThisMonth -> today.withDayOfMonth(1)
        ExportRange.LastThreeMonths -> today.minusMonths(THREE_MONTHS)
        ExportRange.All -> return null
    }
    return start.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** GPS trips with a route in [range], oldest first: what a range export writes. */
fun tripsToExport(trips: List<Trip>, range: ExportRange, nowUtcMs: Long, zone: ZoneId): List<Trip> {
    val from = range.startUtcMs(nowUtcMs, zone) ?: Long.MIN_VALUE
    return trips.filter { it.source != TripSource.Manual && it.status == TripStatus.Done && it.startUtcMs >= from }
        .sortedBy { it.startUtcMs }
}

/** "HSR Layout → Whitefield", else "Trip 2026-10-16 08:10" in local time. */
fun gpxTrackName(trip: Trip, zone: ZoneId): String {
    val from = trip.fromPlace
    val to = trip.toPlace
    return when {
        from != null && to != null -> "$from → $to"
        from != null || to != null -> from ?: to.orEmpty()
        else -> "Trip " + TRACK_TIME.format(Instant.ofEpochMilli(trip.startUtcMs).atZone(zone))
    }
}

private val TRACK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private const val THREE_MONTHS = 3L

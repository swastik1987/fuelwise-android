package app.fuelwise.core.domain.station

import app.fuelwise.core.domain.trip.distanceM
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor

/**
 * What a station sells, from OpenStreetMap's `fuel:*` tags (PRD F-21.2). The order is part of the bundled file's
 * format ([bit] = 1 shl ordinal): add new values at the end, never reorder.
 */
enum class StationFuel {
    Petrol,
    Diesel,
    Cng,
    Lpg,
    Electric,
    ;

    val bit: Int get() = 1 shl ordinal
}

/**
 * A fuel or charging station from the bundled OpenStreetMap list (PRD F-21.2, D16). [brand] is a canonical short
 * name ("HP", "IndianOil") when OSM has one; [name] is OSM's name tag. Data © OpenStreetMap contributors, ODbL.
 */
data class Station(
    val osmId: Long,
    val lat: Double,
    val lng: Double,
    val brand: String?,
    val name: String?,
    val fuels: Set<StationFuel>,
    val charging: Boolean,
) {
    /** "HP · Sri Lakshmi Fuels", or whichever of the two exists. */
    val label: String? get() = listOfNotNull(brand, name?.takeIf { it != brand }).joinToString(" · ").ifEmpty { null }
}

/**
 * The bundled file's format, version 1: a header line, then one station per line, tab-separated:
 * `osmId lat lng flags brand name`, where flags = fuel bits | 32 for a charging station. Coordinates have 5 decimals
 * (about 1 m). Text, gzip-compressed in the APK; the same code writes it (tools:stations) and reads it (the app).
 */
object StationCodec {
    const val HEADER_PREFIX = "fuelwise-stations\tv1"
    private const val CHARGING_FLAG = 32
    private const val FIELDS = 6

    fun encodeLine(s: Station): String {
        val flags = s.fuels.sumOf { it.bit } or (if (s.charging) CHARGING_FLAG else 0)
        return listOf(
            s.osmId.toString(),
            coordinate(s.lat),
            coordinate(s.lng),
            flags.toString(),
            clean(s.brand),
            clean(s.name),
        ).joinToString("\t")
    }

    /** Null for the header, blank lines and anything malformed: a bad line is skipped, never fatal. */
    fun decodeLine(line: String): Station? {
        val parts = line.split('\t').takeIf { it.size == FIELDS && !line.startsWith(HEADER_PREFIX) } ?: return null
        return parse(parts)
    }

    private fun parse(parts: List<String>): Station? {
        val id = parts[ID].toLongOrNull() ?: return null
        val lat = parts[LAT].toDoubleOrNull() ?: return null
        val lng = parts[LNG].toDoubleOrNull()
        val flags = parts[FLAGS].toIntOrNull()
        return if (lng == null || flags == null) {
            null
        } else {
            Station(
                osmId = id,
                lat = lat,
                lng = lng,
                brand = parts[BRAND].ifEmpty { null },
                name = parts[NAME].ifEmpty { null },
                fuels = StationFuel.entries.filterTo(mutableSetOf()) { flags and it.bit != 0 },
                charging = flags and CHARGING_FLAG != 0,
            )
        }
    }

    private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.5f", value)

    private fun clean(text: String?) = text.orEmpty().replace(Regex("[\\t\\r\\n]"), " ").trim()

    private const val ID = 0
    private const val LAT = 1
    private const val LNG = 2
    private const val FLAGS = 3
    private const val BRAND = 4
    private const val NAME = 5
}

/**
 * Nearest-station lookup on the phone (CLAUDE.md rule 5: station matching is offline). A grid of ~1 km cells, so a
 * lookup only measures the stations in the cells around the point.
 */
class StationIndex(stations: List<Station>) {
    private val cells: Map<Long, List<Station>> = stations.groupBy { cellOf(it.lat, it.lng) }

    val size: Int = stations.size

    /** Stations within [radiusM] of the point, nearest first, with their distance in metres. */
    fun near(lat: Double, lng: Double, radiusM: Double): List<Pair<Station, Double>> {
        val latSpan = radiusM / M_PER_DEG
        val lngSpan = radiusM / (M_PER_DEG * cos(Math.toRadians(lat)).coerceAtLeast(MIN_COS))
        val rows = floor((lat - latSpan) / CELL_DEG).toLong()..floor((lat + latSpan) / CELL_DEG).toLong()
        val cols = floor((lng - lngSpan) / CELL_DEG).toLong()..floor((lng + lngSpan) / CELL_DEG).toLong()
        return rows.flatMap { r -> cols.flatMap { c -> cells[key(r, c)].orEmpty() } }
            .map { it to distanceM(lat, lng, it.lat, it.lng) }
            .filter { it.second <= radiusM }
            .sortedBy { it.second }
    }

    private fun cellOf(lat: Double, lng: Double) = key(floor(lat / CELL_DEG).toLong(), floor(lng / CELL_DEG).toLong())

    private fun key(row: Long, col: Long) = row * KEY_STRIDE + col

    private companion object {
        const val CELL_DEG = 0.01
        const val M_PER_DEG = 111_320.0
        const val MIN_COS = 0.1
        const val KEY_STRIDE = 100_000L
    }
}

/** Which of OSM's fuels a vehicle's energy needs, for ranking a CNG car towards CNG pumps (PRD F-21.1 fuel fit). */
fun Station.sells(fuel: StationFuel): Boolean = if (fuel == StationFuel.Electric) charging else fuel in fuels

/**
 * Canonical short brands for India's common station brands, from OSM's `brand` / `operator` / `name` tags, so the
 * same company always reads the same ("IndianOil", not "Indian Oil Corporation Ltd").
 */
fun canonicalBrand(vararg tags: String?): String? {
    val text = tags.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
    return BRANDS.firstOrNull { (_, keys) -> keys.any { it in text } }?.first
}

private val BRANDS = listOf(
    "IndianOil" to listOf("indian oil", "indianoil", "iocl", "ioc "),
    "HP" to listOf("hindustan petroleum", "hpcl", "hp petrol", "hp "),
    "BPCL" to listOf("bharat petroleum", "bpcl"),
    "Jio-bp" to listOf("jio-bp", "jio bp", "reliance"),
    "Nayara" to listOf("nayara", "essar"),
    "Shell" to listOf("shell"),
    "IGL" to listOf("indraprastha gas", "igl"),
    "MGL" to listOf("mahanagar gas", "mgl"),
    "Tata Power" to listOf("tata power"),
    "Statiq" to listOf("statiq"),
    "ChargeZone" to listOf("chargezone", "charge zone"),
)

package app.fuelwise.tools.stations

import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.station.StationCodec
import app.fuelwise.core.domain.station.StationFuel
import app.fuelwise.core.domain.station.StationIndex
import app.fuelwise.core.domain.station.canonicalBrand
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Builds the bundled station list (PRD F-21.2, D16) on a developer's machine. The app never runs this or calls
 * Overpass: it reads the file this writes. Data © OpenStreetMap contributors, ODbL 1.0.
 *
 *   ./gradlew :tools:stations:run --args="fetch core/data/src/main/assets/stations/india.tsv.gz"
 *   ./gradlew :tools:stations:run --args="near core/data/src/main/assets/stations/india.tsv.gz 12.9784 77.6408 300"
 *
 * `near` lists what the file has around a point: for the D14 coverage audit against known pumps.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "fetch" -> fetch(File(args.getOrElse(1) { DEFAULT_OUT }))

        // Rebuild from the cached Overpass answer, without asking Overpass again.
        "rebuild" -> write(parse(CACHE.readText()), File(args.getOrElse(1) { DEFAULT_OUT }))

        "near" -> {
            val (file, lat, lng) = args.drop(1)
            near(
                File(file),
                lat.toDouble(),
                lng.toDouble(),
                args.lastOrNull()?.takeIf { args.size > NEAR_ARGS }?.toDouble() ?: NEAR_RADIUS_M,
            )
        }

        else -> println("usage: fetch <out.tsv.gz> | rebuild <out.tsv.gz> | near <file.tsv.gz> <lat> <lng> [radiusM]")
    }
}

private fun fetch(out: File) {
    println("Asking Overpass for India's fuel and charging stations (a few minutes)…")
    val body = "data=" + URLEncoder.encode(QUERY, Charsets.UTF_8)
    val request = HttpRequest.newBuilder(URI(OVERPASS))
        .timeout(Duration.ofMinutes(TIMEOUT_MIN))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("User-Agent", "fuelwise-stations-build/1 (developer tool; one download per release)")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == HTTP_OK) {
        "Overpass answered ${response.statusCode()}: ${response.body().take(ERROR_PREVIEW)}"
    }
    CACHE.parentFile?.mkdirs()
    CACHE.writeText(response.body())
    write(parse(response.body()), out)
}

private fun write(stations: List<Station>, out: File) {
    out.parentFile?.mkdirs()
    GZIPOutputStream(out.outputStream()).bufferedWriter().use { w ->
        w.write("${StationCodec.HEADER_PREFIX}\t${LocalDate.now()}\t© OpenStreetMap contributors, ODbL 1.0\n")
        stations.sortedBy { it.osmId }.forEach { w.write(StationCodec.encodeLine(it) + "\n") }
    }
    val fuel = stations.count { !it.charging }
    val kb = out.length() / KB
    println("Wrote ${stations.size} stations ($fuel fuel, ${stations.size - fuel} charging) to $out, $kb KB")
}

/** Overpass JSON → stations. Ways and relations come with a centre point (`out center`). */
internal fun parse(json: String): List<Station> {
    val elements = Json.parseToJsonElement(json).jsonObject["elements"]?.jsonArray.orEmpty()
    return elements.mapNotNull { element ->
        val e = element.jsonObject
        val tags = e["tags"]?.jsonObject ?: return@mapNotNull null
        val point = if (e.string("type") == "node") e else e["center"]?.jsonObject ?: return@mapNotNull null
        val lat = point["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val lng = point["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val id = e["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        val charging = tags.string("amenity") == "charging_station"
        val rawName = (tags.string("name:en") ?: tags.string("name"))?.take(MAX_NAME)
        val brand = canonicalBrand(tags.string("brand"), tags.string("operator"), rawName)
            ?: tags.string("brand")?.take(MAX_BRAND)
        Station(
            // Unique across element types: node, way and relation ids can collide.
            osmId = id * TYPE_STRIDE + typeCode(e.string("type")),
            lat = lat,
            lng = lng,
            brand = brand,
            name = rawName?.takeUnless { generic(it, brand) },
            fuels = if (charging) setOf(StationFuel.Electric) else fuels(tags) + namedFuels(rawName),
            charging = charging,
        )
    }
}

/**
 * Names that say nothing the brand doesn't ("Petrol Pump", "Indian Oil" for an IndianOil pump) are dropped, so the
 * chip reads "IndianOil", not "IndianOil · Indian Oil".
 */
private fun generic(name: String, brand: String?): Boolean {
    val words = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    val rest = words.filterNot { it in GENERIC_WORDS }
    return rest.isEmpty() || (brand != null && canonicalBrand(name) == brand && rest.size <= BRAND_ONLY_WORDS)
}

/** "HP CNG", "Auto LPG": OSM often names the fuel without tagging it. */
private fun namedFuels(name: String?): Set<StationFuel> = buildSet {
    val text = name.orEmpty().lowercase()
    if ("cng" in text) add(StationFuel.Cng)
    if ("lpg" in text) add(StationFuel.Lpg)
}

/** OSM `fuel:*=yes` tags. A pump without any (most of India's) sells petrol and diesel. */
private fun fuels(tags: JsonObject): Set<StationFuel> {
    val yes = tags.filter { (k, v) -> k.startsWith("fuel:") && v.jsonPrimitive.contentOrNull == "yes" }.keys
    if (yes.isEmpty()) return setOf(StationFuel.Petrol, StationFuel.Diesel)
    return buildSet {
        if (yes.any { it.startsWith("fuel:octane") || it == "fuel:petrol" }) add(StationFuel.Petrol)
        if (yes.any { it.startsWith("fuel:diesel") }) add(StationFuel.Diesel)
        if ("fuel:cng" in yes) add(StationFuel.Cng)
        if ("fuel:lpg" in yes) add(StationFuel.Lpg)
    }
}

private fun near(file: File, lat: Double, lng: Double, radiusM: Double) {
    val stations = GZIPInputStream(file.inputStream()).bufferedReader().useLines { lines ->
        lines.mapNotNull(StationCodec::decodeLine).toList()
    }
    val found = StationIndex(stations).near(lat, lng, radiusM)
    println("${found.size} of ${stations.size} stations within ${radiusM.toInt()} m of $lat, $lng")
    found.forEach { (s, d) ->
        val fuels = s.fuels.joinToString(",") { it.name }
        println("  ${d.toInt()} m  ${s.label ?: "(unnamed)"}  [$fuels]  osm ${s.osmId / TYPE_STRIDE}")
    }
}

private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null }

private fun typeCode(type: String?) = when (type) {
    "node" -> 0L
    "way" -> 1L
    else -> 2L
}

private const val OVERPASS = "https://overpass-api.de/api/interpreter"
private const val QUERY = """
[out:json][timeout:600];
area["ISO3166-1"="IN"][admin_level=2]->.in;
(
  nwr["amenity"="fuel"](area.in);
  nwr["amenity"="charging_station"](area.in);
);
out center tags;
"""
private val CACHE = File("tools/stations/build/overpass-india.json")
private val GENERIC_WORDS =
    setOf("petrol", "pump", "fuel", "station", "filling", "bunk", "diesel", "cng", "outlet", "ltd")
private const val BRAND_ONLY_WORDS = 3
private const val DEFAULT_OUT = "core/data/src/main/assets/stations/india.tsv.gz"
private const val TIMEOUT_MIN = 12L
private const val HTTP_OK = 200
private const val KB = 1024
private const val ERROR_PREVIEW = 300
private const val NEAR_ARGS = 4
private const val NEAR_RADIUS_M = 300.0
private const val TYPE_STRIDE = 10L
private const val MAX_NAME = 60
private const val MAX_BRAND = 30

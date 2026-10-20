package app.fuelwise.core.domain.prices

import app.fuelwise.core.model.City
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** One city price from the national feed (PRD F-09.2). */
data class FeedPrice(val cityId: String, val fuel: PriceFuel, val date: LocalDate, val price: Money)

data class FeedCity(val city: City, val prices: List<FeedPrice>)

/** The parsed national file: every city, every fuel, today plus 30 days of history (docs/price-feed.md). */
data class PriceFeed(val schema: Int, val generatedAt: Instant?, val cities: List<FeedCity>) {
    /** Filters on the device (CLAUDE.md rule 5): only the cities the user follows are kept. */
    fun pricesFor(cityIds: Set<String>): List<FeedPrice> = cities.filter { it.city.id in cityIds }.flatMap { it.prices }
}

class PriceFeedFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads the feed format in docs/price-feed.md. Tolerant where it can be: unknown fields and fuels are ignored, and a
 * malformed city or an implausible price is skipped, so one bad row never drops the whole file. A file that isn't
 * JSON, or has a newer major [PriceFeed.schema], is rejected.
 */
object PriceFeedParser {
    const val SUPPORTED_SCHEMA = 1

    /** ₹10–₹1,000 per unit: anything outside is a feed error, not a price. */
    private val PLAUSIBLE_PAISE = 1_000L..100_000L

    private val json = Json { ignoreUnknownKeys = true }

    /** Feed keys for each fuel. Permanent: the feed producer uses the same names. */
    val FUEL_KEYS: Map<String, PriceFuel> = mapOf(
        "petrol" to PriceFuel.Petrol,
        "diesel" to PriceFuel.Diesel,
        "cng" to PriceFuel.Cng,
        "premium_petrol" to PriceFuel.PremiumPetrol,
    )

    fun parse(text: String): PriceFeed {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            throw PriceFeedFormatException("Not JSON", e)
        } ?: throw PriceFeedFormatException("Root is not an object")
        val schema = (root["schema"] as? JsonPrimitive)?.intOrNull
            ?: throw PriceFeedFormatException("Missing schema")
        if (schema > SUPPORTED_SCHEMA) throw PriceFeedFormatException("Unsupported schema $schema")
        val cities = (root["cities"] as? JsonArray) ?: throw PriceFeedFormatException("Missing cities")
        return PriceFeed(
            schema = schema,
            generatedAt = root.string("generatedAt")?.let(::parseInstant),
            cities = cities.mapNotNull { (it as? JsonObject)?.let(::parseCity) },
        )
    }

    private fun parseCity(obj: JsonObject): FeedCity? {
        val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: return null
        val city = City(id, obj.string("name") ?: id, obj.string("state").orEmpty())
        val prices = (obj["prices"] as? JsonObject) ?: return FeedCity(city, emptyList())
        return FeedCity(
            city,
            prices.entries.flatMap { (key, series) ->
                val fuel = FUEL_KEYS[key] ?: return@flatMap emptyList()
                (series as? JsonObject)?.let { parseSeries(id, fuel, it) }.orEmpty()
            },
        )
    }

    /** `{"start": "2026-09-19", "paise": [10292, null, …]}`: value i is for start + i days; null is no price. */
    private fun parseSeries(cityId: String, fuel: PriceFuel, series: JsonObject): List<FeedPrice> {
        val start = series.string("start")?.let(::parseDate) ?: return emptyList()
        val values = series["paise"] as? JsonArray ?: return emptyList()
        return values.mapIndexedNotNull { i, value ->
            value.paiseOrNull()?.takeIf { it in PLAUSIBLE_PAISE }?.let {
                FeedPrice(cityId, fuel, start.plusDays(i.toLong()), Money(it))
            }
        }
    }

    private fun JsonElement.paiseOrNull(): Long? =
        if (this is JsonNull) null else (this as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseInstant(text: String): Instant? = try {
        OffsetDateTime.parse(text).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

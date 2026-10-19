package app.fuelwise.core.domain.prices

import app.fuelwise.core.model.City
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** The bundled, versioned city list (assets `prices/cities.json`); [popular] shows as chips in the picker. */
data class CityCatalog(val version: Int, val cities: List<City>, val popular: List<City>) {
    fun byId(id: String?): City? = cities.firstOrNull { it.id == id }

    /** Case-insensitive match on city or state name, in list order; blank returns every city. */
    fun search(query: String): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return cities
        return cities.filter { it.name.contains(q, ignoreCase = true) || it.state.contains(q, ignoreCase = true) }
    }

    companion object {
        val EMPTY = CityCatalog(0, emptyList(), emptyList())

        /** Unknown keys are ignored and rows without an id or name skipped, so an older app reads a newer file. */
        fun parse(text: String): CityCatalog {
            val root = try {
                Json.parseToJsonElement(text) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return EMPTY
            val rows = (root["cities"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val cities = mutableListOf<City>()
            val popular = mutableListOf<City>()
            rows.forEach { row ->
                val id = row.string("id")?.takeIf { it.isNotBlank() } ?: return@forEach
                val name = row.string("name")?.takeIf { it.isNotBlank() } ?: return@forEach
                val city = City(id, name, row.string("state").orEmpty())
                if (cities.none { it.id == id }) cities += city
                if ((row["popular"] as? JsonPrimitive)?.booleanOrNull == true) popular += city
            }
            return CityCatalog((root["version"] as? JsonPrimitive)?.intOrNull ?: 0, cities, popular)
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}

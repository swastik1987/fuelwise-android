package app.fuelwise.sync.drive

import app.fuelwise.core.database.DatabaseDump
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** A decoded backup: the database plus the preferences that travel with it (PRD F-14.4). */
data class Snapshot(
    val format: Int,
    val createdAtUtcMs: Long,
    val deviceId: String,
    val dump: DatabaseDump,
    val preferences: Map<String, Any?>,
)

/**
 * `snapshot-<utc>-<deviceId>.json.gz`: schema-versioned JSON of every row, gzipped. Values keep their SQLite types:
 * integers stay integers, reals keep a decimal point, blobs are base64 objects.
 */
object SnapshotCodec {
    const val FORMAT = 1
    private const val BLOB_KEY = "\$b64"
    private val json = Json { prettyPrint = false }

    fun encode(snapshot: Snapshot): ByteArray {
        val root = JsonObject(
            mapOf(
                "app" to JsonPrimitive("fuelwise"),
                "format" to JsonPrimitive(snapshot.format),
                "schemaVersion" to JsonPrimitive(snapshot.dump.schemaVersion),
                "createdAt" to JsonPrimitive(snapshot.createdAtUtcMs),
                "deviceId" to JsonPrimitive(snapshot.deviceId),
                "tables" to JsonObject(
                    snapshot.dump.tables.mapValues { (_, rows) ->
                        JsonArray(rows.map { row -> JsonObject(row.mapValues { (_, v) -> v.toJson() }) })
                    },
                ),
                "preferences" to JsonObject(snapshot.preferences.mapValues { (_, v) -> v.toJson() }),
            ),
        )
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.encodeToString(JsonObject.serializer(), root).toByteArray()) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Snapshot {
        val text = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().decodeToString() }
        val root = json.parseToJsonElement(text).jsonObject
        require(root["app"]?.jsonPrimitive?.content == "fuelwise") { "Not a Fuelwise backup" }
        val format = root.getValue("format").jsonPrimitive.int
        require(format <= FORMAT) { "Backup format $format is newer than this app" }
        val tables = root.getValue("tables").jsonObject.mapValues { (_, rows) ->
            rows.jsonArray.map { row -> row.jsonObject.mapValues { (_, v) -> v.toValue() } }
        }
        return Snapshot(
            format = format,
            createdAtUtcMs = root.getValue("createdAt").jsonPrimitive.long,
            deviceId = root.getValue("deviceId").jsonPrimitive.content,
            dump = DatabaseDump(root.getValue("schemaVersion").jsonPrimitive.int, tables),
            preferences = root["preferences"]?.jsonObject?.mapValues { (_, v) -> v.toValue() }.orEmpty(),
        )
    }

    private fun Any?.toJson(): JsonElement = when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is ByteArray -> JsonObject(mapOf(BLOB_KEY to JsonPrimitive(Base64.getEncoder().encodeToString(this))))
        is List<*> -> JsonArray(map { it.toJson() })
        else -> JsonPrimitive(toString())
    }

    private fun JsonElement.toValue(): Any? = when (this) {
        JsonNull -> null

        is JsonPrimitive -> when {
            isString -> content

            booleanOrNull != null -> booleanOrNull

            // A real keeps its decimal point or exponent, so 42318.0 comes back a Double and 42318 a Long.
            content.any { it == '.' || it == 'e' || it == 'E' } -> content.toDouble()

            else -> content.toLong()
        }

        is JsonArray -> map { it.toValue() }

        is JsonObject -> this[BLOB_KEY]?.jsonPrimitive?.content?.let { Base64.getDecoder().decode(it) }
    }
}

package app.fuelwise.core.data.repository

import app.fuelwise.core.data.vault.VaultCipher
import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.RouteRetentionPref
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.domain.trip.PrivatePlace
import app.fuelwise.core.domain.trip.RouteRetention
import com.google.protobuf.ByteString
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Location privacy and retention (PRD F-21.6). Device-specific: none of it is in a backup. */
interface LocationPrivacyRepository {
    val retention: Flow<RouteRetention>

    val privatePlaces: Flow<List<PrivatePlace>>

    /** Name trip ends with Android Geocoder (CLAUDE.md rule 5 exception, 12 Nov 2026). On by default. */
    val placeNames: Flow<Boolean>

    suspend fun setPlaceNames(on: Boolean)

    suspend fun setRetention(retention: RouteRetention)

    suspend fun addPrivatePlace(name: String, lat: Double, lng: Double)

    suspend fun removePrivatePlace(id: String)

    /** Removes saved routes older than the retention setting; trip summaries stay. Returns how many went. */
    suspend fun pruneExpiredRoutes(nowUtcMs: Long): Int

    /** "Delete all location history" (separate from "Delete all data"): every saved route and the private places. */
    suspend fun deleteAllLocationHistory()
}

/**
 * Private places, encrypted with the vault key before they reach preferences: home is never stored in the clear
 * (CLAUDE.md rule 5). Shared by the trip repository, which trims route ends with them.
 */
internal class PrivatePlacesStore @Inject constructor(
    private val source: PreferencesDataSource,
    private val cipher: VaultCipher,
) {
    val places: Flow<List<PrivatePlace>> = source.preferences.map { decode(it.privatePlaces.toByteArray()) }

    suspend fun current(): List<PrivatePlace> = places.first()

    suspend fun write(places: List<PrivatePlace>) {
        val bytes = if (places.isEmpty()) ByteArray(0) else cipher.encrypt(encode(places), AD)
        source.update { privatePlaces = ByteString.copyFrom(bytes) }
    }

    private fun decode(bytes: ByteArray): List<PrivatePlace> {
        if (bytes.isEmpty()) return emptyList()
        val text = try {
            cipher.decrypt(bytes, AD).decodeToString()
        } catch (_: GeneralSecurityException) {
            // The key went with a reinstall or "Delete all data": nothing to trim with until places are added again.
            return emptyList()
        }
        return text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            if (parts.size != FIELDS) return@mapNotNull null
            val lat = parts[LAT].toDoubleOrNull() ?: return@mapNotNull null
            val lng = parts[LNG].toDoubleOrNull() ?: return@mapNotNull null
            PrivatePlace(parts[ID], parts[NAME], lat, lng)
        }.toList()
    }

    // One place per line; the separator is stripped from names when they're typed.
    private fun encode(places: List<PrivatePlace>): ByteArray =
        places.joinToString("\n") { listOf(it.id, it.name, it.lat, it.lng).joinToString(SEPARATOR) }.encodeToByteArray()

    private companion object {
        val AD = "private_places".encodeToByteArray()
        const val SEPARATOR = "\t"
        const val ID = 0
        const val NAME = 1
        const val LAT = 2
        const val LNG = 3
        const val FIELDS = 4
    }
}

internal class DataStoreLocationPrivacyRepository @Inject constructor(
    private val source: PreferencesDataSource,
    private val places: PrivatePlacesStore,
    private val trips: TripRepository,
    private val ids: IdGenerator,
) : LocationPrivacyRepository {
    override val retention: Flow<RouteRetention> = source.preferences.map { p ->
        when (p.routeRetention) {
            RouteRetentionPref.ROUTE_RETENTION_30_DAYS -> RouteRetention.Days30
            RouteRetentionPref.ROUTE_RETENTION_1_YEAR -> RouteRetention.Year
            RouteRetentionPref.ROUTE_RETENTION_FOREVER -> RouteRetention.Forever
            else -> RouteRetention.Days90
        }
    }

    override val privatePlaces: Flow<List<PrivatePlace>> = places.places

    override val placeNames: Flow<Boolean> = source.preferences.map { !it.tripPlaceNamesOff }

    override suspend fun setPlaceNames(on: Boolean) = source.update { tripPlaceNamesOff = !on }

    override suspend fun setRetention(retention: RouteRetention) = source.update {
        routeRetention = when (retention) {
            RouteRetention.Days30 -> RouteRetentionPref.ROUTE_RETENTION_30_DAYS
            RouteRetention.Days90 -> RouteRetentionPref.ROUTE_RETENTION_90_DAYS
            RouteRetention.Year -> RouteRetentionPref.ROUTE_RETENTION_1_YEAR
            RouteRetention.Forever -> RouteRetentionPref.ROUTE_RETENTION_FOREVER
        }
    }

    override suspend fun addPrivatePlace(name: String, lat: Double, lng: Double) {
        val clean = name.replace(Regex("[\\t\\n\\r]"), " ").trim().take(MAX_NAME)
        places.write(places.current() + PrivatePlace(ids.newId(), clean, lat, lng))
    }

    override suspend fun removePrivatePlace(id: String) {
        places.write(places.current().filterNot { it.id == id })
    }

    override suspend fun pruneExpiredRoutes(nowUtcMs: Long): Int {
        val cutoff = retention.first().cutoffUtcMs(nowUtcMs) ?: return 0
        return trips.dropRoutes(endedBeforeUtcMs = cutoff)
    }

    override suspend fun deleteAllLocationHistory() {
        trips.dropRoutes(endedBeforeUtcMs = null)
        places.write(emptyList())
    }

    private companion object {
        const val MAX_NAME = 40
    }
}

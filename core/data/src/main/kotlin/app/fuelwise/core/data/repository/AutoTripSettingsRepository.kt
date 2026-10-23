package app.fuelwise.core.data.repository

import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.carLink
import app.fuelwise.core.datastore.copy
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Automatic trips on this phone (PRD F-21.3). Device-specific: not part of a backup. */
data class AutoTripSettings(
    /** The kill switch (CLAUDE.md rule 6): when false the feature isn't offered at all. */
    val available: Boolean,
    /** The user went through setup and turned it on. */
    val enabled: Boolean,
    /** Paused until this UTC ms; [Long.MAX_VALUE] = until turned back on; null = not paused. */
    val pausedUntilUtcMs: Long?,
    /** "Allow all the time" was refused: driving asks "Start trip?" instead of recording on its own. */
    val oneTap: Boolean,
    /** Last activity-detection event, for the battery-saver health check (PRD F-21.7). */
    val lastActivityEventUtcMs: Long?,
    /** Car Bluetooth by vehicle id: connecting starts a trip for that vehicle (PRD F-21.3). */
    val carLinks: Map<String, CarLink> = emptyMap(),
    /** When automatic trips were last turned on; the health check counts silence from here (PRD F-21.7). */
    val enabledAtUtcMs: Long? = null,
) {
    /** The vehicle whose car Bluetooth has [address], if any. */
    fun vehicleFor(address: String): String? =
        carLinks.entries.firstOrNull { it.value.address.equals(address, ignoreCase = true) }?.key

    /** Whether a drive detected at [nowUtcMs] should be acted on. */
    fun isWatching(nowUtcMs: Long): Boolean = available && enabled && (pausedUntilUtcMs ?: 0) <= nowUtcMs
}

/** A vehicle's car Bluetooth, linked through Companion Device Manager on this phone. */
data class CarLink(val address: String, val associationId: Int, val name: String?)

interface AutoTripSettingsRepository {
    val settings: Flow<AutoTripSettings>

    /** On after setup ([oneTap] when background location was refused), or off. Turning off clears any pause. */
    suspend fun setEnabled(enabled: Boolean, oneTap: Boolean = false)

    /** Null resumes now. */
    suspend fun pauseUntil(utcMs: Long?)

    suspend fun recordActivityEvent(utcMs: Long)

    /** Links [vehicleId] to a car's Bluetooth, or unlinks it with null. */
    suspend fun setCarLink(vehicleId: String, link: CarLink?)
}

internal class DataStoreAutoTripSettingsRepository @Inject constructor(
    private val source: PreferencesDataSource,
    private val clock: Clock,
) : AutoTripSettingsRepository {
    override val settings: Flow<AutoTripSettings> = source.preferences.map { p ->
        AutoTripSettings(
            available = !p.featureFlags.automaticTripsOff,
            enabled = p.autoTrips.enabled,
            pausedUntilUtcMs = p.autoTrips.pausedUntil.takeIf { it > 0 },
            oneTap = p.autoTrips.oneTap,
            lastActivityEventUtcMs = p.autoTrips.lastActivityEventAt.takeIf { it > 0 },
            carLinks = p.autoTrips.carLinksMap.mapValues { (_, l) ->
                CarLink(l.address, l.associationId, l.name.ifEmpty { null })
            },
            enabledAtUtcMs = p.autoTrips.enabledAt.takeIf { it > 0 },
        )
    }

    override suspend fun setEnabled(enabled: Boolean, oneTap: Boolean) = source.update {
        val turningOn = enabled && !autoTrips.enabled
        autoTrips = autoTrips.copy {
            this.enabled = enabled
            this.oneTap = enabled && oneTap
            pausedUntil = 0
            if (turningOn) enabledAt = clock.millis()
        }
    }

    override suspend fun pauseUntil(utcMs: Long?) = source.update {
        autoTrips = autoTrips.copy { pausedUntil = utcMs ?: 0 }
    }

    override suspend fun recordActivityEvent(utcMs: Long) = source.update {
        autoTrips = autoTrips.copy { lastActivityEventAt = utcMs }
    }

    override suspend fun setCarLink(vehicleId: String, link: CarLink?) = source.update {
        autoTrips = autoTrips.copy {
            if (link == null) {
                carLinks.remove(vehicleId)
            } else {
                carLinks.put(
                    vehicleId,
                    carLink {
                        address = link.address
                        associationId = link.associationId
                        name = link.name.orEmpty()
                    },
                )
            }
        }
    }
}

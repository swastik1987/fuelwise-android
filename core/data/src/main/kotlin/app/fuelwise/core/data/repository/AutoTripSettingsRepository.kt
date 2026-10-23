package app.fuelwise.core.data.repository

import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.copy
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
) {
    /** Whether a drive detected at [nowUtcMs] should be acted on. */
    fun isWatching(nowUtcMs: Long): Boolean = available && enabled && (pausedUntilUtcMs ?: 0) <= nowUtcMs
}

interface AutoTripSettingsRepository {
    val settings: Flow<AutoTripSettings>

    /** On after setup ([oneTap] when background location was refused), or off. Turning off clears any pause. */
    suspend fun setEnabled(enabled: Boolean, oneTap: Boolean = false)

    /** Null resumes now. */
    suspend fun pauseUntil(utcMs: Long?)

    suspend fun recordActivityEvent(utcMs: Long)
}

internal class DataStoreAutoTripSettingsRepository @Inject constructor(private val source: PreferencesDataSource) :
    AutoTripSettingsRepository {
    override val settings: Flow<AutoTripSettings> = source.preferences.map { p ->
        AutoTripSettings(
            available = !p.featureFlags.automaticTripsOff,
            enabled = p.autoTrips.enabled,
            pausedUntilUtcMs = p.autoTrips.pausedUntil.takeIf { it > 0 },
            oneTap = p.autoTrips.oneTap,
            lastActivityEventUtcMs = p.autoTrips.lastActivityEventAt.takeIf { it > 0 },
        )
    }

    override suspend fun setEnabled(enabled: Boolean, oneTap: Boolean) = source.update {
        autoTrips = autoTrips.copy {
            this.enabled = enabled
            this.oneTap = enabled && oneTap
            pausedUntil = 0
        }
    }

    override suspend fun pauseUntil(utcMs: Long?) = source.update {
        autoTrips = autoTrips.copy { pausedUntil = utcMs ?: 0 }
    }

    override suspend fun recordActivityEvent(utcMs: Long) = source.update {
        autoTrips = autoTrips.copy { lastActivityEventAt = utcMs }
    }
}

package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.AutoTripSettings
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [AutoTripSettingsRepository]; available but not turned on, as on a fresh install. */
class FakeAutoTripSettingsRepository(
    initial: AutoTripSettings = AutoTripSettings(
        available = true,
        enabled = false,
        pausedUntilUtcMs = null,
        oneTap = false,
        lastActivityEventUtcMs = null,
    ),
) : AutoTripSettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setEnabled(enabled: Boolean, oneTap: Boolean) =
        settings.update { it.copy(enabled = enabled, oneTap = enabled && oneTap, pausedUntilUtcMs = null) }

    override suspend fun pauseUntil(utcMs: Long?) = settings.update { it.copy(pausedUntilUtcMs = utcMs) }

    override suspend fun recordActivityEvent(utcMs: Long) = settings.update { it.copy(lastActivityEventUtcMs = utcMs) }
}

package app.fuelwise.core.data.repository

import android.content.Context
import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.prices.CityCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fuel price choices (PRD F-09). The home city itself is [UserData.homeCityId], shared with onboarding and
 * Settings; this holds what only the prices feature uses.
 */
data class PriceSettings(
    val homeCityId: String? = null,
    /** Up to [MAX_PINNED] extra cities to compare, in pinned order (PRD F-09.1). */
    val pinnedCityIds: List<String> = emptyList(),
    val alerts: Boolean = false,
    /** The feed flag (PRD D2/D3 open): off by default, and nothing is fetched without a feed URL either. */
    val feedEnabled: Boolean = false,
    val lastFeedFetchUtcMs: Long? = null,
) {
    /** Home first, then pinned: the cities whose prices the app keeps. */
    val followedCityIds: Set<String> get() = (listOfNotNull(homeCityId) + pinnedCityIds).toSet()

    companion object {
        const val MAX_PINNED = 3
    }
}

interface PriceSettingsRepository {
    val settings: Flow<PriceSettings>

    /** Keeps at most [PriceSettings.MAX_PINNED], without duplicates or the home city. */
    suspend fun setPinned(cityIds: List<String>)

    suspend fun setAlerts(enabled: Boolean)

    suspend fun markFeedFetched(atUtcMs: Long)
}

internal class DataStorePriceSettingsRepository @Inject constructor(private val source: PreferencesDataSource) :
    PriceSettingsRepository {
    override val settings: Flow<PriceSettings> = source.preferences.map { p ->
        PriceSettings(
            homeCityId = p.homeCityId.ifEmpty { null },
            pinnedCityIds = p.pinnedCityIdsList.toList(),
            alerts = p.priceAlerts,
            feedEnabled = p.featureFlags.priceFeed,
            lastFeedFetchUtcMs = p.priceFeedLastFetchAt.takeIf { it > 0 },
        )
    }

    override suspend fun setPinned(cityIds: List<String>) = source.update {
        val clean = cityIds.distinct().filter { it.isNotBlank() && it != homeCityId }.take(PriceSettings.MAX_PINNED)
        pinnedCityIds.clear()
        pinnedCityIds.addAll(clean)
    }

    override suspend fun setAlerts(enabled: Boolean) = source.update { priceAlerts = enabled }

    override suspend fun markFeedFetched(atUtcMs: Long) = source.update { priceFeedLastFetchAt = atUtcMs }
}

/** The bundled city list (assets `prices/cities.json`, versioned). */
interface CityRepository {
    suspend fun catalog(): CityCatalog
}

@Singleton
internal class AssetCityRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : CityRepository {
    private val mutex = Mutex()
    private var cached: CityCatalog? = null

    override suspend fun catalog(): CityCatalog = mutex.withLock {
        cached ?: withContext(io) {
            CityCatalog.parse(context.assets.open(CITIES_FILE).bufferedReader().use { it.readText() })
        }.also { cached = it }
    }

    private companion object {
        const val CITIES_FILE = "prices/cities.json"
    }
}

package app.fuelwise.core.data.repository

import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.copy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What fill-up prompts (PRD F-21.4) remember: "Not a fill-up" per station, and the last prompt per vehicle. */
data class FillUpPromptState(val rejections: Map<Long, Int>, val lastPromptUtcMs: Map<String, Long>)

/** Device-only: none of this is in a backup. */
interface FillUpPromptRepository {
    val state: Flow<FillUpPromptState>

    suspend fun recordPrompt(vehicleId: String, atUtcMs: Long)

    /** "Not a fill-up" at the OSM station [osmId]; three and it's never prompted again. */
    suspend fun recordRejection(osmId: Long)
}

internal class DataStoreFillUpPromptRepository @Inject constructor(private val source: PreferencesDataSource) :
    FillUpPromptRepository {
    override val state: Flow<FillUpPromptState> = source.preferences.map { p ->
        FillUpPromptState(p.fillUpPrompts.rejectionsMap, p.fillUpPrompts.lastPromptAtMap)
    }

    override suspend fun recordPrompt(vehicleId: String, atUtcMs: Long) = source.update {
        fillUpPrompts = fillUpPrompts.copy { lastPromptAt.put(vehicleId, atUtcMs) }
    }

    override suspend fun recordRejection(osmId: Long) = source.update {
        fillUpPrompts = fillUpPrompts.copy { rejections.put(osmId, (rejections[osmId] ?: 0) + 1) }
    }
}

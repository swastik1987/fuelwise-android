package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.FillUpPromptRepository
import app.fuelwise.core.data.repository.FillUpPromptState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [FillUpPromptRepository]. */
class FakeFillUpPromptRepository : FillUpPromptRepository {
    override val state = MutableStateFlow(FillUpPromptState(emptyMap(), emptyMap()))

    override suspend fun recordPrompt(vehicleId: String, atUtcMs: Long) =
        state.update { it.copy(lastPromptUtcMs = it.lastPromptUtcMs + (vehicleId to atUtcMs)) }

    override suspend fun recordRejection(osmId: Long) =
        state.update { it.copy(rejections = it.rejections + (osmId to (it.rejections[osmId] ?: 0) + 1)) }
}

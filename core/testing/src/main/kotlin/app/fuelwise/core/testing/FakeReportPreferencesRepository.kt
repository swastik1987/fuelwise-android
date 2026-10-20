package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.ReportPreferencesRepository
import app.fuelwise.core.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [ReportPreferencesRepository]. */
class FakeReportPreferencesRepository(rate: Money? = null) : ReportPreferencesRepository {
    private val state = MutableStateFlow(rate)
    override val businessRatePerKm: StateFlow<Money?> = state

    override suspend fun setBusinessRatePerKm(rate: Money?) {
        state.value = rate?.takeIf { it.paise > 0 }
    }
}

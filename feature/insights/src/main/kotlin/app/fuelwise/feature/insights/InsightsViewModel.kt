package app.fuelwise.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.domain.insights.InsightPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(private val source: InsightsSource) : ViewModel() {
    private data class Controls(val allVehicles: Boolean = false, val period: InsightPeriod = InsightPeriod.Month)

    private val controls = MutableStateFlow(Controls())

    /** null until the first snapshot loads. */
    val state: StateFlow<InsightsSnapshot?> = controls
        .flatMapLatest { source.observe(it.allVehicles, it.period) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun selectPeriod(period: InsightPeriod) = controls.update { it.copy(period = period) }

    /** null = "All vehicles". */
    fun selectVehicle(id: String?) {
        controls.update { it.copy(allVehicles = id == null) }
        if (id != null) viewModelScope.launch { source.select(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, source: InsightsSource) :
    ViewModel() {
    private val route = savedStateHandle.toRoute<ChartDetailRoute>()
    val chart: ChartKind = ChartKind.valueOf(route.chart)

    val state: StateFlow<InsightsSnapshot?> = source.observe(route.allVehicles, InsightPeriod.valueOf(route.period))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

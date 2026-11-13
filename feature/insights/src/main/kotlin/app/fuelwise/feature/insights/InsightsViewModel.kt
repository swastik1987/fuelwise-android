package app.fuelwise.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.domain.insights.CustomRange
import app.fuelwise.core.domain.insights.InsightPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
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
class InsightsViewModel @Inject constructor(private val source: InsightsSource, savedStateHandle: SavedStateHandle) :
    ViewModel() {
    private data class Controls(
        val allVehicles: Boolean = false,
        val period: InsightPeriod = InsightPeriod.Month,
        val custom: CustomRange? = null,
    )

    // A past month's card on Home opens Insights on that whole month (InsightsRoute.month).
    private val controls = MutableStateFlow(
        savedStateHandle.get<String>(InsightsRoute::month.name)?.let(::controlsFor) ?: Controls(),
    )

    private fun controlsFor(month: String): Controls? {
        val picked = runCatching { YearMonth.parse(month) }.getOrNull() ?: return null
        return Controls(period = InsightPeriod.Custom, custom = CustomRange(picked.atDay(1), picked.atEndOfMonth()))
    }

    /** null until the first snapshot loads. */
    val state: StateFlow<InsightsSnapshot?> = controls
        .flatMapLatest { source.observe(it.allVehicles, it.period, it.custom) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun selectPeriod(period: InsightPeriod) = controls.update { it.copy(period = period) }

    /** A picked range (PRD F-06 "custom date range"), both days included. */
    fun selectCustom(from: LocalDate, to: LocalDate) = controls.update {
        it.copy(period = InsightPeriod.Custom, custom = CustomRange(minOf(from, to), maxOf(from, to)))
    }

    /** null = "All vehicles". */
    fun selectVehicle(id: String?) {
        controls.update { it.copy(allVehicles = id == null) }
        if (id != null) viewModelScope.launch { source.select(id) }
    }

    fun setOwnershipYears(years: Int) {
        viewModelScope.launch { source.setOwnershipYears(years) }
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

    private val custom = if (route.fromEpochDay != null && route.toEpochDay != null) {
        CustomRange(LocalDate.ofEpochDay(route.fromEpochDay), LocalDate.ofEpochDay(route.toEpochDay))
    } else {
        null
    }

    val state: StateFlow<InsightsSnapshot?> = source.observe(
        route.allVehicles,
        InsightPeriod.valueOf(route.period),
        custom,
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

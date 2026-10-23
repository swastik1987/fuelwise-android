package app.fuelwise.feature.insights

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.fuelwise.core.domain.insights.InsightPeriod
import kotlinx.serialization.Serializable

@Serializable
data object InsightsRoute

/**
 * Chart detail (design.md screen 15). [chart] is a [ChartKind] name, [period] an [InsightPeriod] name; a custom
 * period carries its days as epoch days.
 */
@Serializable
data class ChartDetailRoute(
    val chart: String,
    val period: String,
    val allVehicles: Boolean,
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
)

/** Monthly recap card (design.md screen 16). [month] is "2026-09"; null means last month. */
@Serializable
data class RecapRoute(val month: String? = null)

fun NavGraphBuilder.insightsScreen(
    onAddEntry: () -> Unit,
    onOpenChart: (ChartDetailRoute) -> Unit,
    onOpenRecap: () -> Unit,
) {
    composable<InsightsRoute> {
        InsightsScreen(
            onAddEntry = onAddEntry,
            onOpenChart = { kind, period, all, custom ->
                onOpenChart(
                    ChartDetailRoute(kind.name, period.name, all, custom?.from?.toEpochDay(), custom?.to?.toEpochDay()),
                )
            },
            onOpenRecap = onOpenRecap,
        )
    }
}

fun NavGraphBuilder.chartDetailScreen(onBack: () -> Unit) {
    composable<ChartDetailRoute> { ChartDetailScreen(onBack = onBack) }
}

fun NavGraphBuilder.recapScreen(onBack: () -> Unit) {
    composable<RecapRoute> { RecapScreen(onBack = onBack) }
}

internal val InsightPeriod.label: Int
    get() = when (this) {
        InsightPeriod.Month -> R.string.period_month
        InsightPeriod.ThreeMonths -> R.string.period_3m
        InsightPeriod.SixMonths -> R.string.period_6m
        InsightPeriod.YearToDate -> R.string.period_ytd
        InsightPeriod.TwelveMonths -> R.string.period_12m
        InsightPeriod.All -> R.string.period_all
        InsightPeriod.Custom -> R.string.period_custom
    }

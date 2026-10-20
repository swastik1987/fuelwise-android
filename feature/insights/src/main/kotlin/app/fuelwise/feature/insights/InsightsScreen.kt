package app.fuelwise.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.Delta
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwHeroMetric
import app.fuelwise.core.designsystem.component.FwInfoSheet
import app.fuelwise.core.designsystem.component.FwMetricTile
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSectionHeader
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.insights.InsightCard
import app.fuelwise.core.domain.insights.InsightPeriod
import app.fuelwise.core.domain.insights.InsightsReport
import app.fuelwise.core.domain.insights.ProInsight
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.UnitFormat
import app.fuelwise.core.ui.format.efficiencyText
import app.fuelwise.core.ui.format.efficiencyUnit
import app.fuelwise.core.ui.format.efficiencyValue
import app.fuelwise.core.ui.vehicle.AllVehiclesPill
import app.fuelwise.core.ui.vehicle.VehicleSwitcherPill
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class InsightsActions(
    val onAddEntry: () -> Unit = {},
    val onOpenChart: (ChartKind, InsightPeriod, Boolean) -> Unit = { _, _, _ -> },
    val onOpenRecap: () -> Unit = {},
    val onPeriod: (InsightPeriod) -> Unit = {},
    val onSelectVehicle: (String?) -> Unit = {},
)

@Composable
internal fun InsightsScreen(
    onAddEntry: () -> Unit,
    onOpenChart: (ChartKind, InsightPeriod, Boolean) -> Unit,
    onOpenRecap: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsContent(
        state,
        InsightsActions(onAddEntry, onOpenChart, onOpenRecap, viewModel::selectPeriod, viewModel::selectVehicle),
    )
}

/** Insights overview (design.md screen 14). */
@Composable
internal fun InsightsContent(state: InsightsSnapshot?, actions: InsightsActions) {
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        if (state == null) return@Column
        val report = state.report
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(
                WindowInsets.statusBars,
            ).height(64.dp).padding(horizontal = FwTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val vehicle = state.selected
            when {
                vehicle == null -> Unit
                state.allVehicles -> AllVehiclesPill(onClick = { switcherOpen = true })
                else -> VehicleSwitcherPill(vehicle, onClick = { switcherOpen = true })
            }
        }
        Row(
            Modifier.horizontalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InsightPeriod.entries.forEach { p ->
                FwFilterChip(stringResource(p.label), p == state.period, { actions.onPeriod(p) })
            }
        }
        if (report == null || report.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FwEmptyState(
                    icon = FwIcons.Insights,
                    headline = stringResource(R.string.insights_empty_title),
                    body = stringResource(R.string.insights_empty_body),
                    actionLabel = stringResource(R.string.insights_empty_action),
                    onAction = actions.onAddEntry,
                )
            }
        } else {
            Overview(state, report, actions) { info = it }
        }
    }
    if (switcherOpen && state != null) {
        VehicleSwitcherSheet(
            vehicles = state.vehicles,
            selectedId = if (state.allVehicles) null else state.selected?.id,
            onSelect = {
                actions.onSelectVehicle(it)
                switcherOpen = false
            },
            onAddVehicle = { switcherOpen = false },
            onDismiss = { switcherOpen = false },
            showAllVehicles = state.vehicles.size > 1,
        )
    }
    info?.let { which ->
        val (title, body) = when (which) {
            INFO_COST_PER_KM -> R.string.insights_info_cpk_title to R.string.insights_info_cpk_body
            else -> R.string.insights_info_tiles_title to R.string.insights_info_tiles_body
        }
        FwInfoSheet(stringResource(title), listOf(stringResource(body)), onDismiss = { info = null })
    }
}

@Composable
private fun Overview(
    state: InsightsSnapshot,
    report: InsightsReport,
    actions: InsightsActions,
    onInfo: (String) -> Unit,
) {
    val m = report.metrics
    val quantity = report.energyType?.canonicalUnit ?: QuantityUnit.LITRE
    Column(
        Modifier.verticalScroll(
            rememberScrollState(),
        ).padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
    ) {
        FwHeroMetric(
            overline = stringResource(R.string.insights_hero_overline, stringResource(state.period.label)),
            value = m.costPerKm?.let { IndianNumberFormat.rupees(it) } ?: "—",
            unit = stringResource(R.string.insights_per_km),
            delta = report.costPerKmChange?.let { costDelta(it) },
            onInfoClick = { onInfo(INFO_COST_PER_KM) },
            infoContentDescription = stringResource(R.string.insights_info_cpk_title),
            valueStyle = FwTheme.type.display,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwMetricTile(
                    overline = stringResource(R.string.insights_total_spent),
                    value = IndianNumberFormat.rupeesWhole(m.totalSpend),
                    caption = m.excludedExpenses.takeIf { it.paise > 0 }
                        ?.let { stringResource(R.string.insights_excluded, IndianNumberFormat.rupeesWhole(it)) },
                    modifier = Modifier.weight(1f),
                )
                FwMetricTile(
                    overline = stringResource(R.string.insights_distance),
                    value = m.distance?.let {
                        IndianNumberFormat.decimal(state.units.distance.fromKm(it.km), 0)
                    } ?: "—",
                    unit = stringResource(UnitFormat.distance(state.units.distance)),
                    caption = m.distance?.takeIf {
                        it.isEstimated
                    }?.let { stringResource(R.string.insights_estimated) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (report.energyType != null) {
                    FwMetricTile(
                        overline = stringResource(R.string.insights_avg_mileage),
                        value = efficiencyValue(report.average?.kmPerUnit, quantity, state.units),
                        unit = efficiencyUnit(quantity, state.units),
                        caption = report.best?.let {
                            stringResource(
                                R.string.insights_best,
                                efficiencyValue(it.kmPerUnit, quantity, state.units),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    FwMetricTile(
                        overline = stringResource(R.string.insights_fill_ups),
                        value = report.fillCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                FwMetricTile(
                    overline = stringResource(R.string.insights_cost_per_day),
                    value = IndianNumberFormat.rupeesWhole(m.costPerDay),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onInfo(INFO_TILES) }) {
                    Icon(
                        painterResource(FwIcons.Info),
                        stringResource(R.string.insights_info_tiles_title),
                        tint = FwTheme.colors.ink2,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (report.cards.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FwOverline(stringResource(R.string.insights_cards))
                report.cards.forEach { InsightCardView(it, quantity, state.units) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FwOverline(stringResource(R.string.insights_charts))
            ChartKind.entries.filter { !it.needsOneVehicle || report.energyType != null }.forEach { kind ->
                FwCard(onClick = {
                    actions.onOpenChart(kind, state.period, state.allVehicles)
                }, contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(kind.title),
                            style = FwTheme.type.label,
                            color = FwTheme.colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(painterResource(FwIcons.ChevronRight), null, tint = FwTheme.colors.ink3)
                    }
                    ChartPreview(kind, report, state.units, Modifier.padding(top = 10.dp))
                }
            }
        }
        if (!state.allVehicles) {
            FwSectionHeader(title = stringResource(R.string.recap_title))
            FwCard(onClick = actions.onOpenRecap, contentPadding = PaddingValues(16.dp)) {
                Text(stringResource(R.string.recap_entry), style = FwTheme.type.body, color = FwTheme.colors.ink)
            }
        }
    }
}

@Composable
private fun InsightCardView(card: InsightCard, quantity: QuantityUnit, units: DisplayUnits) {
    FwCard(contentPadding = PaddingValues(16.dp)) {
        when (card) {
            is InsightCard.MileageDrop -> {
                FwOverline(stringResource(R.string.card_mileage_drop_title))
                Body(stringResource(R.string.card_mileage_drop_body, percent(card.fraction)))
            }

            is InsightCard.RealVsClaimed -> {
                FwOverline(stringResource(R.string.card_real_vs_claimed_title))
                Body(
                    stringResource(
                        R.string.card_real_vs_claimed_body,
                        percent(card.ratio),
                        efficiencyText(card.actualKmPerUnit, quantity, units),
                        efficiencyText(card.claimedKmPerUnit, quantity, units),
                    ),
                )
            }

            is InsightCard.Locked -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FwOverline(stringResource(card.kind.title), Modifier.weight(1f))
                    Icon(
                        painterResource(FwIcons.Lock),
                        null,
                        tint = FwTheme.colors.ink3,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    "₹ •••",
                    style = FwTheme.type.h2,
                    color = FwTheme.colors.ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(stringResource(R.string.card_unlock), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = FwTheme.type.body, color = FwTheme.colors.ink, modifier = Modifier.padding(top = 6.dp))
}

private val ProInsight.title: Int
    get() = when (this) {
        ProInsight.FuelGrade -> R.string.card_pro_fuel_grade
        ProInsight.CngSaving -> R.string.card_pro_cng
        ProInsight.EvVsPetrol -> R.string.card_pro_ev_vs_petrol
        ProInsight.HomeVsPublic -> R.string.card_pro_home_public
        ProInsight.Forecast -> R.string.card_pro_forecast
        ProInsight.CompareVehicles -> R.string.card_pro_compare
    }

@Composable
private fun costDelta(change: Double): Delta {
    val pct = (abs(change) * PERCENT).roundToInt()
    val direction = when {
        pct == 0 -> Delta.Direction.Flat
        change < 0 -> Delta.Direction.Down
        else -> Delta.Direction.Up
    }
    return Delta(
        label = stringResource(R.string.insights_delta_previous, pct),
        direction = direction,
        isImprovement = if (direction == Delta.Direction.Flat) null else change < 0,
    )
}

private fun percent(fraction: Double): Int = (fraction * PERCENT).roundToInt()

private const val PERCENT = 100
private const val INFO_COST_PER_KM = "cpk"
private const val INFO_TILES = "tiles"

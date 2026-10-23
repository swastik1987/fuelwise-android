package app.fuelwise.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwInfoSheet
import app.fuelwise.core.designsystem.component.FwMetricTile
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.insights.InsightsReport
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.sum
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.efficiencyText

@Composable
internal fun ChartDetailScreen(onBack: () -> Unit, viewModel: ChartDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChartDetailContent(viewModel.chart, state, onBack)
}

/** Chart detail (design.md screen 15): summary numbers, the chart, a data table toggle and the formula sheet. */
@Composable
internal fun ChartDetailContent(kind: ChartKind, state: InsightsSnapshot?, onBack: () -> Unit) {
    var showTable by rememberSaveable { mutableStateOf(false) }
    var showFormula by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(kind.title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.insights_back),
            actions = {
                IconButton(onClick = { showFormula = true }) {
                    Icon(
                        painterResource(FwIcons.Info),
                        stringResource(R.string.insights_how_calculated),
                        tint = FwTheme.colors.ink,
                    )
                }
            },
        )
        val report = state?.report ?: return@Column
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
        ) {
            Text(
                periodLabel(state),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink2,
            )
            Summary(kind, report, state.units)
            if (report.series(kind, state.units).isEmpty()) {
                Text(stringResource(R.string.chart_no_data), style = FwTheme.type.body, color = FwTheme.colors.ink2)
            } else {
                ChartView(kind, report, state.categories, state.units, cityPrices = state.cityPrices)
                FwTextButton(
                    stringResource(if (showTable) R.string.chart_hide_table else R.string.chart_show_table),
                    { showTable = !showTable },
                )
                if (showTable) DataTable(report.rows(kind, state.categories, state.units, state.cityPrices))
            }
        }
    }
    if (showFormula) {
        FwInfoSheet(stringResource(kind.title), listOf(stringResource(kind.formula)), onDismiss = {
            showFormula = false
        })
    }
}

@Composable
private fun Summary(kind: ChartKind, report: InsightsReport, units: DisplayUnits) {
    val unit = report.energyUnit()
    val quantity = report.energyType?.canonicalUnit ?: QuantityUnit.LITRE
    val tiles: List<Pair<String, String>> = when (kind) {
        ChartKind.Mileage -> listOfNotNull(
            report.average?.let {
                stringResource(R.string.summary_average) to
                    efficiencyText(it.kmPerUnit, quantity, units)
            },
            report.best?.let {
                stringResource(R.string.summary_best) to
                    efficiencyText(it.kmPerUnit, quantity, units)
            },
        )

        ChartKind.MonthlySpend -> listOf(
            stringResource(R.string.summary_total) to IndianNumberFormat.rupeesWhole(report.metrics.totalSpend),
            stringResource(R.string.summary_per_month) to IndianNumberFormat.rupeesWhole(report.metrics.monthlyBurn),
        )

        ChartKind.PricePaid -> listOfNotNull(
            report.averageUnitPrice?.let {
                stringResource(R.string.summary_average) to
                    IndianNumberFormat.rupees(it) + "/$unit"
            },
            report.averageFillQuantity?.let {
                stringResource(R.string.summary_avg_fill) to
                    "${IndianNumberFormat.decimal(it, 1)} $unit"
            },
        )

        ChartKind.CostPerKm -> listOfNotNull(
            report.metrics.costPerKm?.let {
                stringResource(R.string.summary_period) to
                    IndianNumberFormat.rupees(it) + "/km"
            },
            report.metrics.energyCostPerKm?.let {
                stringResource(R.string.summary_energy_only) to
                    IndianNumberFormat.rupees(it) + "/km"
            },
        )

        ChartKind.SpendByCategory -> listOf(
            stringResource(R.string.summary_total) to IndianNumberFormat.rupeesWhole(
                report.spendByCategory.map { it.amount }.sum().takeIf { it.paise > 0 } ?: Money.ZERO,
            ),
        )

        ChartKind.Odometer -> listOfNotNull(
            report.metrics.distance?.let {
                stringResource(R.string.summary_distance) to
                    "${IndianNumberFormat.decimal(it.km, 0)} km"
            },
        )
    }
    if (tiles.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { (label, value) ->
            FwMetricTile(overline = label, value = value, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DataTable(rows: List<ChartRow>) {
    Column {
        Text(
            stringResource(R.string.chart_data_table),
            style = FwTheme.type.label,
            color = FwTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2, modifier = Modifier.weight(1f))
                Text(row.value, style = FwTheme.type.label, color = FwTheme.colors.ink)
            }
            HorizontalDivider(color = FwTheme.colors.line)
        }
    }
}

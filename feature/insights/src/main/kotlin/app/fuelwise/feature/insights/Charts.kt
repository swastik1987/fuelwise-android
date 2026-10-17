package app.fuelwise.feature.insights

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.component.FwSparkline
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.insights.InsightsReport
import app.fuelwise.core.model.Category
import app.fuelwise.core.ui.entry.categoryName
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleLabels
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/** The chart list on Insights (design.md screen 14) and the chart detail (screen 15). */
enum class ChartKind(
    @param:StringRes val title: Int,
    @param:StringRes val formula: Int,
    /** Mileage, prices and the odometer belong to one vehicle. */
    val needsOneVehicle: Boolean,
) {
    Mileage(R.string.chart_mileage, R.string.chart_mileage_formula, true),
    MonthlySpend(R.string.chart_monthly_spend, R.string.chart_monthly_spend_formula, false),
    PricePaid(R.string.chart_price_paid, R.string.chart_price_paid_formula, true),
    CostPerKm(R.string.chart_cost_per_km, R.string.chart_cost_per_km_formula, false),
    SpendByCategory(R.string.chart_by_category, R.string.chart_by_category_formula, false),
    Odometer(R.string.chart_odometer, R.string.chart_odometer_formula, true),
}

/** One data-table row: what the chart plots at a point, as text (accessibility + export, design.md 15). */
internal data class ChartRow(val label: String, val value: String)

/** Plotted values for [kind], oldest first. Money is plotted in rupees. */
internal fun InsightsReport.series(kind: ChartKind): List<Double> = when (kind) {
    ChartKind.Mileage -> mileage.map { it.interval.kmPerUnit }
    ChartKind.MonthlySpend -> monthlySpend.map { it.total.paise / PAISE }
    ChartKind.PricePaid -> prices.map { it.unitPrice.paise / PAISE }
    ChartKind.CostPerKm -> costPerKmByMonth.mapNotNull { it.costPerKm?.paise?.div(PAISE) }
    ChartKind.SpendByCategory -> spendByCategory.map { it.amount.paise / PAISE }
    ChartKind.Odometer -> odometer.map { it.odometerKm }
}

@Composable
internal fun InsightsReport.rows(kind: ChartKind, categories: Map<String, Category>): List<ChartRow> {
    val unit = energyUnit()
    return when (kind) {
        ChartKind.Mileage -> mileage.map {
            ChartRow(shortDate(it.atUtcMs), "${IndianNumberFormat.decimal(it.interval.kmPerUnit, 1)} km/$unit")
        }

        ChartKind.MonthlySpend -> monthlySpend.map {
            ChartRow(monthLabel(it.month.monthValue, it.month.year), IndianNumberFormat.rupeesWhole(it.total))
        }

        ChartKind.PricePaid -> prices.map {
            ChartRow(
                shortDate(it.atUtcMs),
                IndianNumberFormat.rupees(it.unitPrice) + "/$unit",
            )
        }

        ChartKind.CostPerKm -> costPerKmByMonth.map {
            ChartRow(
                monthLabel(it.month.monthValue, it.month.year),
                it.costPerKm?.let { c -> IndianNumberFormat.rupees(c) + "/km" } ?: "—",
            )
        }

        ChartKind.SpendByCategory -> spendByCategory.map { spend ->
            val name =
                spend.categoryId?.let { categories[it] }?.let { categoryName(it) }
                    ?: stringResource(R.string.chart_fuel)
            ChartRow(name, IndianNumberFormat.rupeesWhole(spend.amount))
        }

        ChartKind.Odometer -> odometer.map {
            ChartRow(shortDate(it.atUtcMs), "${IndianNumberFormat.decimal(it.odometerKm, 0)} km")
        }
    }
}

@Composable
internal fun InsightsReport.energyUnit(): String =
    energyType?.let { stringResource(VehicleLabels.unit(it.canonicalUnit)) }.orEmpty()

@Composable
internal fun InsightsReport.energyColor(): Color =
    energyType?.let { VehicleLabels.energyColor(it) } ?: FwTheme.colors.ink

/** Mini preview on the chart list card. */
@Composable
internal fun ChartPreview(kind: ChartKind, report: InsightsReport, modifier: Modifier = Modifier) {
    val values = report.series(kind)
    when (kind) {
        ChartKind.MonthlySpend, ChartKind.SpendByCategory -> MiniBars(
            if (kind == ChartKind.SpendByCategory) values.take(MINI_BARS) else values.takeLast(MINI_BARS),
            FwTheme.colors.ink,
            modifier,
        )

        else -> FwSparkline(
            values,
            modifier,
            color = if (kind ==
                ChartKind.Mileage
            ) {
                report.energyColor()
            } else {
                FwTheme.colors.ink
            },
        )
    }
}

@Composable
private fun MiniBars(values: List<Double>, color: Color, modifier: Modifier) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: return
    Row(
        modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { v ->
            Box(
                Modifier.weight(1f).fillMaxHeight((v / max).toFloat().coerceIn(MIN_BAR, 1f))
                    .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}

/** Full chart on the detail screen. */
@Composable
@Suppress("SpreadOperator") // Vico takes layers as varargs; the array is two items at most.
internal fun ChartView(
    kind: ChartKind,
    report: InsightsReport,
    categories: Map<String, Category>,
    modifier: Modifier = Modifier,
) {
    if (kind == ChartKind.SpendByCategory) {
        CategoryBars(report.rows(kind, categories), report.series(kind), modifier)
        return
    }
    val labels = when (kind) {
        ChartKind.Mileage -> report.mileage.map { shortDate(it.atUtcMs) }

        ChartKind.PricePaid -> report.prices.map { shortDate(it.atUtcMs) }

        ChartKind.Odometer -> report.odometer.map { shortDate(it.atUtcMs) }

        ChartKind.MonthlySpend -> report.monthlySpend.map { monthLabel(it.month.monthValue, null) }

        ChartKind.CostPerKm -> report.costPerKmByMonth.filter {
            it.costPerKm != null
        }.map { monthLabel(it.month.monthValue, null) }

        ChartKind.SpendByCategory -> emptyList()
    }
    val values = report.series(kind)
    if (values.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(report, kind) {
        producer.runTransaction {
            when (kind) {
                ChartKind.Mileage -> {
                    columnModel { series(values) }
                    lineModel { series(report.mileage.map { it.rollingKmPerUnit }) }
                }

                ChartKind.MonthlySpend -> columnModel {
                    series(report.monthlySpend.map { it.energy.paise / PAISE })
                    series(report.monthlySpend.map { it.expenses.paise / PAISE })
                }

                else -> lineModel { series(values) }
            }
        }
    }
    val colors = FwTheme.colors
    val axisLabel = rememberAxisLabelComponent(style = FwTheme.type.caption.copy(color = colors.ink2))
    val axisLine = rememberAxisLineComponent(fill = Fill(colors.line))
    val guideline = rememberAxisGuidelineComponent(fill = Fill(colors.line))
    val bottomFormatter =
        remember(labels) { CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.toInt()).orEmpty() } }
    val energy = report.energyColor()
    val claimed = report.claimedKmPerUnit.takeIf { kind == ChartKind.Mileage }
    // Keep the claimed line inside the plot even when every tank is below it.
    val range = remember(claimed) {
        object : CartesianLayerRangeProvider {
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
                ceil(maxOf(maxY, (claimed ?: 0.0) * CLAIMED_HEADROOM, 1.0))
        }
    }
    val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    val columns = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
            rememberLineComponent(Fill(if (kind == ChartKind.Mileage) energy else colors.ink), 10.dp, barShape),
            rememberLineComponent(Fill(colors.ink3), 10.dp, barShape),
        ),
        mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
        rangeProvider = range,
    )
    val line = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = remember(colors.ink) { LineCartesianLayer.LineFill.single(Fill(colors.ink)) },
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
            ),
        ),
        rangeProvider = range,
    )
    val claimedLabel = claimed?.let { stringResource(R.string.chart_claimed, IndianNumberFormat.decimal(it, 1)) }
    val claimedText = rememberTextComponent(style = FwTheme.type.caption.copy(color = colors.ink2))
    val claimedLine = rememberLineComponent(Fill(colors.ink2), 1.dp)
    val decorations = remember(claimed, claimedLabel, claimedText, claimedLine) {
        if (claimed == null || claimedLabel == null) {
            emptyList()
        } else {
            listOf(
                HorizontalLine(y = {
                    claimed
                }, line = claimedLine, labelComponent = claimedText, label = { claimedLabel }),
            )
        }
    }
    val layers = when (kind) {
        ChartKind.Mileage -> arrayOf(columns, line)
        ChartKind.MonthlySpend -> arrayOf(columns)
        else -> arrayOf(line)
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            *layers,
            startAxis = VerticalAxis.rememberStart(
                line = axisLine,
                label = axisLabel,
                guideline = guideline,
                tick = null,
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = axisLine,
                label = axisLabel,
                guideline = null,
                tick = null,
                valueFormatter = bottomFormatter,
            ),
            decorations = decorations,
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(240.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = values.size > SCROLL_AFTER),
        initialAnimationSpec = null,
    )
}

/** Horizontal bars, largest first (PRD F-06.3: no pie charts). */
@Composable
private fun CategoryBars(rows: List<ChartRow>, values: List<Double>, modifier: Modifier) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.zip(values).forEach { (row, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(
                        row.label,
                        style = FwTheme.type.bodyS,
                        color = FwTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(row.value, style = FwTheme.type.label, color = FwTheme.colors.ink)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(FwTheme.colors.surface2, FwTheme.radius.pill)) {
                    Box(
                        Modifier.fillMaxWidth((value / max).toFloat().coerceIn(MIN_BAR, 1f)).fillMaxHeight()
                            .background(FwTheme.colors.ink, FwTheme.radius.pill),
                    )
                }
            }
        }
    }
}

private fun shortDate(utcMs: Long): String = DateTimeFormatter.ofPattern(
    "d MMM",
    Locale.ENGLISH,
).format(Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault()))

private fun monthLabel(month: Int, year: Int?): String {
    val name = java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    return if (year == null) name else "$name $year"
}

private const val PAISE = 100.0
private const val MINI_BARS = 6
private const val MIN_BAR = 0.04f
private const val SCROLL_AFTER = 12
private const val CLAIMED_HEADROOM = 1.08

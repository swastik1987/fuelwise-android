package app.fuelwise.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.component.Delta
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwDeltaChip
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPagerDots
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.summary.MonthSummary
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.efficiencyUnit
import app.fuelwise.core.ui.format.efficiencyValue
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a month card reads its numbers: units, privacy mode, and whether mileage is only ever an estimate. */
internal data class MonthCardStyle(
    val quantity: QuantityUnit,
    val units: DisplayUnits,
    val hideAmounts: Boolean,
    /** Bi-fuel: mileage is always an estimate. */
    val mileageEstimate: Boolean,
)

/**
 * Home's month cards (user feedback, 12 Nov 2026): a full-width pager, this month first, swiping back through earlier
 * months. Replaces the "This month · cost per km" hero and the four tiles. A card opens Insights on its month; the
 * trips cell opens Trips filtered to it.
 */
@Composable
internal fun MonthPager(months: List<MonthSummary>, style: MonthCardStyle, actions: HomeActions, onInfo: () -> Unit) {
    if (months.isEmpty()) return
    val pager = rememberPagerState { months.size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pager,
            pageSpacing = TILE_GAP,
            verticalAlignment = Alignment.Top,
            key = { months[it].month.toString() },
        ) { page ->
            val month = months[page]
            MonthCard(
                month = month,
                style = style,
                // This month opens Insights as it is; a past month opens on that whole month.
                onOpen = { actions.onOpenMonthInsights(month.month.takeIf { page != 0 }) },
                onOpenTrips = { actions.onOpenMonthTrips(month.month) },
                onInfo = onInfo,
                current = page == 0,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FwPagerDots(
                count = months.size,
                selected = pager.currentPage,
                description = stringResource(R.string.home_month_page, pager.currentPage + 1, months.size),
            )
        }
    }
}

@Composable
private fun MonthCard(
    month: MonthSummary,
    style: MonthCardStyle,
    onOpen: () -> Unit,
    onOpenTrips: () -> Unit,
    onInfo: () -> Unit,
    current: Boolean,
) {
    val metrics = month.metrics
    FwCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), contentPadding = CARD_PADDING) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FwOverline(monthTitle(month.month, current), Modifier.weight(1f))
            IconButton(onClick = onInfo, modifier = Modifier.size(INFO_TARGET)) {
                Icon(
                    painterResource(FwIcons.Info),
                    contentDescription = stringResource(R.string.home_cost_per_km_title),
                    tint = FwTheme.colors.ink2,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                metrics.costPerKm?.let { money(it, style.hideAmounts, whole = false) } ?: "—",
                style = FwTheme.type.displayXl,
                color = FwTheme.colors.ink,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                stringResource(R.string.home_per_km),
                style = FwTheme.type.h2.copy(fontWeight = FontWeight.Medium),
                color = FwTheme.colors.ink2,
                modifier = Modifier.alignByBaseline(),
            )
        }
        month.costPerKmChange?.let { FwDeltaChip(costDelta(it, month.month.minusMonths(1))) }
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                Stat(
                    label = stringResource(
                        if (month.mileageCarried || style.mileageEstimate) {
                            R.string.home_mileage_est
                        } else {
                            R.string.home_mileage
                        },
                    ),
                    value = efficiencyValue(month.mileage?.kmPerUnit, style.quantity, style.units),
                    unit = efficiencyUnit(style.quantity, style.units).takeIf { month.mileage != null },
                    caption = stringResource(R.string.home_month_mileage_carried).takeIf { month.mileageCarried },
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    label = stringResource(
                        if (metrics.distance?.isEstimated == true) R.string.home_driven_est else R.string.home_driven,
                    ),
                    value = metrics.distance?.let { IndianNumberFormat.decimal(it.km, 0) } ?: "—",
                    unit = stringResource(R.string.home_km).takeIf { metrics.distance != null },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                Stat(
                    label = stringResource(R.string.home_trips),
                    value = IndianNumberFormat.decimal(month.trips.count.toDouble(), 0),
                    caption = stringResource(
                        R.string.home_km_value,
                        IndianNumberFormat.decimal(month.trips.distanceKm, 0),
                    )
                        .takeIf { month.trips.count > 0 },
                    onClick = onOpenTrips,
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    // Fuel used plus expenses (PRD F-03.5), so it isn't ₹0 in a month without a fill-up.
                    label = stringResource(
                        if (metrics.energyEstimated) R.string.home_spent_est else R.string.home_spent,
                    ),
                    value = money(metrics.totalSpend, style.hideAmounts, whole = true),
                    caption = paidCaption(metrics.paidEnergy, metrics.energyEstimated, style.hideAmounts),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One of the four numbers on a month card, on the warm inner surface. [onClick] makes it its own tap target. */
@Composable
private fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val tappable = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Column(
        modifier
            .fillMaxHeight()
            .heightIn(min = STAT_MIN_HEIGHT)
            .clip(FwTheme.radius.card)
            .background(FwTheme.colors.surface2)
            .then(tappable)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = FwTheme.type.h3, color = FwTheme.colors.ink, modifier = Modifier.alignByBaseline())
            if (unit != null) {
                Text(
                    unit,
                    style = FwTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (caption != null) Text(caption, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
    }
}

/** "Paid at the pump ₹2,400" when the spend is fuel used, so the money actually paid is still in view. */
@Composable
private fun paidCaption(paid: Money, estimated: Boolean, hide: Boolean): String? =
    if (estimated) stringResource(R.string.home_paid_at_pump, money(paid, hide, whole = true)) else null

/** "November · so far" for this month; "October", or "December 2025" outside this year. */
@Composable
private fun monthTitle(month: YearMonth, current: Boolean): String {
    val name = monthName(month, TextStyle.FULL)
    return when {
        current -> stringResource(R.string.home_month_so_far, name)
        month.year == YearMonth.now().year -> name
        else -> "$name ${month.year}"
    }
}

@Composable
private fun monthName(month: YearMonth, style: TextStyle): String =
    month.month.getDisplayName(style, LocalConfiguration.current.locales[0])

@Composable
private fun costDelta(change: Double, previous: YearMonth): Delta {
    val pct = (abs(change) * PERCENT).roundToInt()
    val direction = when {
        pct == 0 -> Delta.Direction.Flat
        change < 0 -> Delta.Direction.Down
        else -> Delta.Direction.Up
    }
    return Delta(
        label = stringResource(R.string.home_delta_vs, pct, monthName(previous, TextStyle.SHORT)),
        direction = direction,
        isImprovement = if (direction == Delta.Direction.Flat) null else change < 0,
    )
}

private val CARD_PADDING = androidx.compose.foundation.layout.PaddingValues(18.dp)
private val INFO_TARGET = 48.dp
private val STAT_MIN_HEIGHT = 72.dp

package app.fuelwise.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.Delta
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwHeroMetric
import app.fuelwise.core.designsystem.component.FwInfoSheet
import app.fuelwise.core.designsystem.component.FwMetricTile
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSectionHeader
import app.fuelwise.core.designsystem.component.FwSparkline
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.insights.Milestone
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.domain.summary.VehicleSummary
import app.fuelwise.core.domain.trip.MonthTrips
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.entry.LogbookEntryRow
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.efficiencyText
import app.fuelwise.core.ui.format.efficiencyUnit
import app.fuelwise.core.ui.format.efficiencyValue
import app.fuelwise.core.ui.format.quantityText
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.core.ui.vehicle.VehicleSwitcherPill
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Navigation out of Home; kept in one place so the screen signature stays short. */
data class HomeActions(
    val onLogFuel: () -> Unit,
    val onAddVehicle: () -> Unit,
    val onOpenEntry: (EntrySource, String) -> Unit,
    val onOpenLogbook: () -> Unit,
    val onOpenReminders: () -> Unit = {},
    val onOpenReminder: (String) -> Unit = {},
    val onOpenTrips: () -> Unit = {},
    /** Starts a GPS trip, asking for location first if needed; opens the live trip if one is already recording. */
    val onStartTrip: () -> Unit = {},
)

@Composable
internal fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state,
        actions,
        viewModel::selectVehicle,
        viewModel::setHideAmounts,
        viewModel::dismissMilestone,
        viewModel::confirmAutoEntries,
    )
}

/** Home (design.md screen 5): ₹/km hero, mileage and spend tiles, last fill-up, range, insight, recent entries. */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    onSelectVehicle: (String?) -> Unit,
    onHideAmounts: (Boolean) -> Unit,
    onDismissMilestone: (String) -> Unit = {},
    onConfirmAuto: () -> Unit = {},
) {
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var heroInfo by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        when (state) {
            HomeUiState.Loading -> Unit

            HomeUiState.NoVehicles -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FwEmptyState(
                    icon = FwIcons.Car,
                    headline = stringResource(R.string.home_no_vehicle_title),
                    body = stringResource(R.string.home_no_vehicle_body),
                    actionLabel = stringResource(R.string.home_no_vehicle_action),
                    onAction = actions.onAddVehicle,
                )
            }

            is HomeUiState.Ready -> {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp)
                        .padding(start = FwTheme.spacing.gutter, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VehicleSwitcherPill(state.vehicle, onClick = { switcherOpen = true })
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onHideAmounts(!state.hideAmounts) }) {
                        Icon(
                            painterResource(if (state.hideAmounts) FwIcons.VisibilityOff else FwIcons.Visibility),
                            stringResource(
                                if (state.hideAmounts) R.string.home_show_amounts else R.string.home_hide_amounts,
                            ),
                            tint = FwTheme.colors.ink,
                        )
                    }
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState())
                        .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.m),
                    verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
                ) {
                    if (state.summary.hasMileage) {
                        Dashboard(state, actions) { heroInfo = true }
                    } else {
                        BrandNewVehicle(state.summary.fullFillCount, actions.onLogFuel)
                        TripTiles(state.trips, actions)
                    }
                    CareCards(state, actions, onConfirmAuto)
                    if (state.recent.isNotEmpty()) Recent(state, actions)
                }
                if (switcherOpen) {
                    VehicleSwitcherSheet(
                        vehicles = state.vehicles,
                        selectedId = state.vehicle.id,
                        onSelect = {
                            onSelectVehicle(it)
                            switcherOpen = false
                        },
                        onAddVehicle = {
                            switcherOpen = false
                            actions.onAddVehicle()
                        },
                        onDismiss = { switcherOpen = false },
                    )
                }
                state.milestone?.let { milestone ->
                    FwInfoSheet(
                        title = stringResource(milestone.title),
                        paragraphs = listOf(milestoneBody(milestone, state)),
                        onDismiss = { onDismissMilestone(milestone.key) },
                    )
                }
                if (heroInfo) {
                    FwInfoSheet(
                        title = stringResource(R.string.home_cost_per_km_title),
                        paragraphs = listOf(
                            stringResource(R.string.home_cost_per_km_body1),
                            stringResource(R.string.home_cost_per_km_body2),
                        ),
                        onDismiss = { heroInfo = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun Dashboard(state: HomeUiState.Ready, actions: HomeActions, onHeroInfo: () -> Unit) {
    val s = state.summary
    val hide = state.hideAmounts
    val quantity = state.vehicle.primaryTank?.energyType?.canonicalUnit ?: QuantityUnit.LITRE
    FwHeroMetric(
        overline = stringResource(R.string.home_hero_overline),
        value = s.month.costPerKm?.let { money(it, hide, whole = false) } ?: "—",
        unit = stringResource(R.string.home_per_km),
        delta = s.costPerKmChange?.let { costDelta(it, state.previousMonthLabel) },
        onInfoClick = onHeroInfo,
        infoContentDescription = stringResource(R.string.home_cost_per_km_title),
    )
    Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        MetricTiles(s, hide, quantity, state.units)
        TripTiles(state.trips, actions)
    }
    s.lastRefuel?.let { last ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FwOverline(stringResource(R.string.home_last_fill))
            FwCard(onClick = {
                actions.onOpenEntry(EntrySource.Refuel, last.id)
            }, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            listOf(
                                shortDate(last.dateTimeUtcMs, last.zoneId),
                                quantityText(last.quantity, quantity, state.units),
                            )
                                .joinToString(" · "),
                            style = FwTheme.type.label,
                            color = FwTheme.colors.ink,
                        )
                        last.stationName?.let { Text(it, style = FwTheme.type.caption, color = FwTheme.colors.ink2) }
                    }
                    Text(money(last.paid, hide, whole = true), style = FwTheme.type.label, color = FwTheme.colors.ink)
                }
                s.lastRefuelInterval?.let {
                    Text(
                        efficiencyText(it.kmPerUnit, quantity, state.units),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink,
                        modifier = Modifier.padding(top = 8.dp).background(FwTheme.colors.surface2, FwTheme.radius.pill)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                s.rangeLeftKm?.let { RangeGauge(it, state, s) }
            }
        }
    }
    s.mileageDrop?.let { drop ->
        FwCard(contentPadding = PaddingValues(16.dp)) {
            FwOverline(stringResource(R.string.home_insight))
            Text(
                stringResource(R.string.home_insight_mileage_drop, (drop.fraction * PERCENT).roundToInt()),
                style = FwTheme.type.body,
                color = FwTheme.colors.ink,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MetricTiles(s: VehicleSummary, hide: Boolean, quantity: QuantityUnit, units: DisplayUnits) {
    // Mileage in the chosen unit (PRD F-18); L/100 km reads lower-is-better, so the sparkline follows the shown value.
    fun shown(kmPerUnit: Double) = units.efficiency(kmPerUnit, quantity)?.value
    Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        FwMetricTile(
            overline = stringResource(if (s.isEstimate) R.string.home_mileage_est else R.string.home_mileage),
            value = efficiencyValue(s.latestInterval?.kmPerUnit, quantity, units),
            unit = efficiencyUnit(quantity, units),
            caption = s.averageInterval?.let {
                stringResource(R.string.home_mileage_avg, efficiencyValue(it.kmPerUnit, quantity, units))
            },
            modifier = Modifier.weight(1f),
            extra = {
                FwSparkline(s.recentIntervals.mapNotNull { shown(it.kmPerUnit) }, Modifier.padding(top = 8.dp))
            },
        )
        FwMetricTile(
            overline = stringResource(R.string.home_spent),
            value = money(s.month.totalSpend, hide, whole = true),
            caption = s.month.distance?.let {
                stringResource(R.string.home_spent_km, IndianNumberFormat.decimal(it.km, 0))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** This month's trips (tap → Trips) beside a one-tap GPS trip start; design.md 5 says Trips is reachable from Home. */
@Composable
private fun TripTiles(trips: MonthTrips, actions: HomeActions) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        FwMetricTile(
            overline = stringResource(R.string.home_trips),
            value = IndianNumberFormat.decimal(trips.count.toDouble(), 0),
            caption = stringResource(R.string.home_spent_km, IndianNumberFormat.decimal(trips.distanceKm, 0)),
            onClick = actions.onOpenTrips,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        FwCard(
            onClick = actions.onStartTrip,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(18.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(FwTheme.colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(FwIcons.Route),
                    contentDescription = null,
                    tint = FwTheme.colors.onAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                stringResource(R.string.home_start_trip),
                style = FwTheme.type.label,
                color = FwTheme.colors.ink,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.home_start_trip_caption),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun RangeGauge(rangeKm: Double, state: HomeUiState.Ready, s: VehicleSummary) {
    val fullRange = state.vehicle.custom.rangeKm
        ?: state.vehicle.primaryTank?.capacity?.let { cap -> s.averageInterval?.let { cap * it.kmPerUnit } }
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.home_range_left, IndianNumberFormat.decimal(rangeKm, 0)),
            style = FwTheme.type.caption,
            color = FwTheme.colors.ink2,
        )
        if (fullRange != null && fullRange > 0) {
            val fraction = (rangeKm / fullRange).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).background(FwTheme.colors.surface2, FwTheme.radius.pill)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(FwTheme.colors.ink, FwTheme.radius.pill))
            }
        }
    }
}

@Composable
private fun Recent(state: HomeUiState.Ready, actions: HomeActions) {
    Column {
        FwSectionHeader(
            title = stringResource(R.string.home_recent),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = actions.onOpenLogbook,
        )
        state.recent.forEach { entry: LogbookEntry ->
            LogbookEntryRow(
                entry = entry,
                vehicle = state.vehicle,
                categories = state.categories,
                kmPerUnit = state.kmPerUnit[entry.id],
                units = state.units,
                onClick = { actions.onOpenEntry(entry.source, entry.id) },
                amountText = money(entry.amount, state.hideAmounts, whole = true),
            )
        }
    }
}

@Composable
private fun costDelta(change: Double, previousMonth: String): Delta {
    val pct = (abs(change) * PERCENT).roundToInt()
    val direction = when {
        pct == 0 -> Delta.Direction.Flat
        change < 0 -> Delta.Direction.Down
        else -> Delta.Direction.Up
    }
    return Delta(
        label = stringResource(R.string.home_delta_vs, pct, previousMonth),
        direction = direction,
        isImprovement = if (direction == Delta.Direction.Flat) null else change < 0,
    )
}

/** Privacy mode masks every ₹ figure (design.md 5, "Privacy blur"). */
private fun money(value: Money, hide: Boolean, whole: Boolean): String = when {
    hide -> HIDDEN_AMOUNT
    whole -> IndianNumberFormat.rupeesWhole(value)
    else -> IndianNumberFormat.rupees(value)
}

private fun shortDate(utcMs: Long, zoneId: String): String {
    val date = Instant.ofEpochMilli(utcMs).atZone(
        runCatching {
            ZoneId.of(zoneId)
        }.getOrDefault(ZoneId.systemDefault()),
    ).toLocalDate()
    return IndianNumberFormat.date(date)
}

private const val PERCENT = 100
private val TILE_GAP = 12.dp
private const val HIDDEN_AMOUNT = "₹ •••"

private val Milestone.title: Int
    get() = when (kind) {
        Milestone.Kind.FirstThousandKm -> R.string.milestone_1000_title
        Milestone.Kind.TenThousandKm -> R.string.milestone_10000_title
        Milestone.Kind.OneYear -> R.string.milestone_year_title
        Milestone.Kind.BestEverTank -> R.string.milestone_best_title
    }

@Composable
private fun milestoneBody(milestone: Milestone, state: HomeUiState.Ready): String = when (milestone.kind) {
    Milestone.Kind.FirstThousandKm -> stringResource(R.string.milestone_1000_body, state.vehicle.name)

    Milestone.Kind.TenThousandKm -> stringResource(R.string.milestone_10000_body, state.vehicle.name)

    Milestone.Kind.OneYear -> stringResource(R.string.milestone_year_body)

    Milestone.Kind.BestEverTank -> {
        val unit = state.vehicle.primaryTank?.let {
            stringResource(VehicleLabels.unit(it.energyType.canonicalUnit))
        }.orEmpty()
        stringResource(R.string.milestone_best_body, IndianNumberFormat.decimal(milestone.kmPerUnit ?: 0.0, 1), unit)
    }
}

/** Up to three reminder pills and the recurring-entries confirm card (design.md screen 5; PRD F-04.3, F-11). */
@Composable
private fun CareCards(state: HomeUiState.Ready, actions: HomeActions, onConfirmAuto: () -> Unit) {
    val care = state.care
    if (care.unconfirmed.isNotEmpty()) {
        FwCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                pluralStringResource(R.plurals.home_auto_entries, care.unconfirmed.size, care.unconfirmed.size),
                style = FwTheme.type.body,
                color = FwTheme.colors.ink,
            )
            Text(
                money(
                    care.unconfirmed.map {
                        it.amount
                    }.fold(Money.ZERO) { a, b -> a + b },
                    state.hideAmounts,
                    whole = true,
                ),
                style = FwTheme.type.label,
                color = FwTheme.colors.ink2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FwTextButton(stringResource(R.string.home_auto_confirm), onConfirmAuto)
                FwTextButton(stringResource(R.string.home_auto_review), actions.onOpenLogbook)
            }
        }
    }
    if (care.upcoming.isEmpty()) return
    Column {
        FwSectionHeader(
            title = stringResource(R.string.home_upcoming),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = actions.onOpenReminders,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            care.upcoming.forEach { status ->
                val r = status.reminder
                val tone = when (status.urgency) {
                    Urgency.Overdue -> FwTheme.colors.negative
                    Urgency.Due -> FwTheme.colors.warning
                    else -> FwTheme.colors.ink2
                }
                Row(
                    Modifier.clip(FwTheme.radius.pill).background(FwTheme.colors.surface).clickable {
                        actions.onOpenReminder(r.id)
                    }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).background(tone, CircleShape))
                    Text(
                        listOf(
                            r.title ?: stringResource(CareLabels.reminder(r.kind)),
                            pillDue(status),
                        ).joinToString(" · "),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun pillDue(status: ReminderStatus): String {
    val days = status.daysLeft
    val km = status.kmLeft
    return when {
        days != null && (km == null || days <= KM_DAYS_TIE) -> when {
            days < 0 -> stringResource(R.string.home_pill_overdue)
            days == 0L -> stringResource(R.string.home_pill_today)
            else -> pluralStringResource(R.plurals.home_pill_days, days.toInt(), days.toInt())
        }

        km != null ->
            if (km <
                0
            ) {
                stringResource(R.string.home_pill_overdue)
            } else {
                stringResource(R.string.home_pill_km, IndianNumberFormat.decimal(km, 0))
            }

        else -> ""
    }
}

private const val KM_DAYS_TIE = 30L

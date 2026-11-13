package app.fuelwise.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwInfoSheet
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSectionHeader
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.insights.Milestone
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.domain.summary.VehicleSummary
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.entry.LogbookEntryRow
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.efficiencyText
import app.fuelwise.core.ui.format.quantityText
import app.fuelwise.core.ui.price.PriceLabels
import app.fuelwise.core.ui.vehicle.VehicleSwitcherPill
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/** Navigation out of Home; kept in one place so the screen signature stays short. */
data class HomeActions(
    val onLogFuel: () -> Unit,
    val onAddVehicle: () -> Unit,
    val onOpenEntry: (EntrySource, String) -> Unit,
    val onOpenLogbook: () -> Unit,
    val onOpenReminders: () -> Unit = {},
    val onOpenReminder: (String) -> Unit = {},
    /** A month card: Insights on that month; null for this month (Insights as it is). */
    val onOpenMonthInsights: (YearMonth?) -> Unit = {},
    /** A month card's trips: Trips filtered to that month. */
    val onOpenMonthTrips: (YearMonth) -> Unit = {},
    val onOpenPrices: () -> Unit = {},
    /** Garage: vehicles, reminders, documents, backup and Settings (Trips took its bottom-nav tab). */
    val onOpenGarage: () -> Unit = {},
    /** The battery-saver fix (PRD F-21.7), from the amber pill. */
    val onFixBattery: () -> Unit = {},
)

@Composable
internal fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoTrips by viewModel.autoTrips.collectAsStateWithLifecycle()
    HomeContent(
        state,
        actions,
        viewModel::selectVehicle,
        viewModel::setHideAmounts,
        viewModel::dismissMilestone,
        viewModel::confirmAutoEntries,
        AutoTripControls(autoTrips, viewModel::pauseAutoTrips, viewModel::resumeAutoTrips, actions.onFixBattery),
    )
}

/** Home (design.md screen 5): month cards, last fill-up and range, care, prices, insight, recent entries. */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    onSelectVehicle: (String?) -> Unit,
    onHideAmounts: (Boolean) -> Unit,
    onDismissMilestone: (String) -> Unit = {},
    onConfirmAuto: () -> Unit = {},
    autoTrips: AutoTripControls = AutoTripControls(),
) {
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var pauseSheet by rememberSaveable { mutableStateOf(false) }
    var heroInfo by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        when (state) {
            HomeUiState.Loading -> Unit

            HomeUiState.NoVehicles -> Column {
                // Garage stays reachable before the first vehicle: Settings, restore and import live there.
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp)
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) { GarageButton(actions.onOpenGarage) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FwEmptyState(
                        icon = FwIcons.Car,
                        headline = stringResource(R.string.home_no_vehicle_title),
                        body = stringResource(R.string.home_no_vehicle_body),
                        actionLabel = stringResource(R.string.home_no_vehicle_action),
                        onAction = actions.onAddVehicle,
                    )
                }
            }

            is HomeUiState.Ready -> {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp)
                        .padding(start = FwTheme.spacing.gutter, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VehicleSwitcherPill(state.vehicle, onClick = { switcherOpen = true })
                    Spacer(Modifier.weight(1f))
                    autoTrips.pill?.let { pill ->
                        AutoTripsStatus(pill) {
                            if (pill.blocked &&
                                pill.pausedUntilUtcMs == null
                            ) {
                                autoTrips.onFixBattery()
                            } else {
                                pauseSheet = true
                            }
                        }
                    }
                    IconButton(onClick = { onHideAmounts(!state.hideAmounts) }) {
                        Icon(
                            painterResource(if (state.hideAmounts) FwIcons.VisibilityOff else FwIcons.Visibility),
                            stringResource(
                                if (state.hideAmounts) R.string.home_show_amounts else R.string.home_hide_amounts,
                            ),
                            tint = FwTheme.colors.ink,
                        )
                    }
                    GarageButton(actions.onOpenGarage)
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState())
                        .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.m),
                    verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
                ) {
                    if (!state.summary.hasMileage) BrandNewVehicle(state.summary.fullFillCount, actions.onLogFuel)
                    MonthPager(
                        months = state.summary.months,
                        style = MonthCardStyle(
                            quantity = state.vehicle.primaryTank?.energyType?.canonicalUnit ?: QuantityUnit.LITRE,
                            units = state.units,
                            hideAmounts = state.hideAmounts,
                            mileageEstimate = state.summary.isEstimate,
                        ),
                        actions = actions,
                        onInfo = { heroInfo = true },
                    )
                    if (state.summary.hasMileage) Dashboard(state, actions)
                    CareCards(state, actions, onConfirmAuto)
                    state.prices?.let { PriceStrip(it, actions.onOpenPrices) }
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
                if (pauseSheet && autoTrips.pill != null) {
                    AutoTripsPauseSheet(autoTrips) { pauseSheet = false }
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

/** Below the month cards once mileage is unlocked: last fill-up with range, and the mileage insight. */
@Composable
private fun Dashboard(state: HomeUiState.Ready, actions: HomeActions) {
    val s = state.summary
    val hide = state.hideAmounts
    val quantity = state.vehicle.primaryTank?.energyType?.canonicalUnit ?: QuantityUnit.LITRE
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

/** Opens Garage from the top bar (design.md §6: Trips has the bottom-nav slot). */
@Composable
private fun GarageButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(FwIcons.Garage), stringResource(R.string.home_open_garage), tint = FwTheme.colors.ink)
    }
}

/** "Bengaluru · Petrol ₹102.92 ▲ · Diesel ₹88.95 · CNG ₹89.00" (design.md screen 5). Tap → Prices. */
@Composable
private fun PriceStrip(prices: HomePrices, onOpen: () -> Unit) {
    val parts = prices.summary.rows.mapNotNull { row ->
        val latest = row.latest ?: return@mapNotNull null
        val change = row.change?.paise ?: 0L
        val arrow = when {
            change > 0 -> " ▲"
            change < 0 -> " ▼"
            else -> ""
        }
        stringResource(PriceLabels.fuel(row.fuel)) + " " + IndianNumberFormat.rupees(latest.price) + arrow
    }
    val line = if (parts.isEmpty()) {
        stringResource(R.string.home_prices_empty, prices.cityName)
    } else {
        (listOf(prices.cityName) + parts).joinToString(" · ")
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FwOverline(stringResource(R.string.home_prices_today))
        FwCard(onClick = onOpen, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                line,
                style = FwTheme.type.label,
                color = FwTheme.colors.ink,
                maxLines = 1,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
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

/** Privacy mode masks every ₹ figure (design.md 5, "Privacy blur"). */
internal fun money(value: Money, hide: Boolean, whole: Boolean): String = when {
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

internal const val PERCENT = 100
internal val TILE_GAP = 12.dp
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
        val quantity = state.vehicle.primaryTank?.energyType?.canonicalUnit ?: QuantityUnit.LITRE
        stringResource(R.string.milestone_best_body, efficiencyText(milestone.kmPerUnit ?: 0.0, quantity, state.units))
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

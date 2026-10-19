package app.fuelwise.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwEntryRow
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.ui.entry.shortDate
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun TripsScreen(links: TripsLinks, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TripsEvent.Deleted -> {
                    // Deletes offer undo for 10 seconds (PRD F-02 AC).
                    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                        snackbar.showSnackbar(
                            message = resources.getString(R.string.trips_deleted),
                            actionLabel = resources.getString(R.string.trips_undo),
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == null) snackbar.currentSnackbarData?.dismiss()
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.token)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        TripsContent(state, links)
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Trips (design.md screen 17): weekly groups with totals, newest first. GPS recording joins in M8 slice 2. */
@Composable
internal fun TripsContent(state: TripsUiState, links: TripsLinks, zone: ZoneId = ZoneId.systemDefault()) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.trips_title),
            onBack = links.onBack,
            backContentDescription = stringResource(R.string.trips_back),
        )
        if (state.loading) return@Column
        if (state.weeks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.hasVehicles) {
                    FwEmptyState(
                        icon = FwIcons.Route,
                        headline = stringResource(R.string.trips_empty),
                        body = stringResource(R.string.trips_empty_body),
                        actionLabel = stringResource(R.string.trips_add_manual),
                        onAction = links.onAddManual,
                    )
                } else {
                    FwEmptyState(
                        icon = FwIcons.Route,
                        headline = stringResource(R.string.trips_no_vehicles),
                        body = stringResource(R.string.trips_no_vehicles_body),
                    )
                }
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = FwTheme.spacing.xl),
        ) {
            item {
                FwInkButton(
                    stringResource(R.string.trips_add_manual),
                    links.onAddManual,
                    Modifier.fillMaxWidth().padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
                    leadingIcon = FwIcons.ManualTrip,
                )
            }
            state.weeks.forEach { week ->
                item(key = "week-${week.start}") { WeekHeader(week) }
                items(week.rows, key = { it.trip.id }) { row -> TripRowItem(row, zone, links.onOpenTrip) }
            }
        }
    }
}

@Composable
private fun WeekHeader(week: TripWeekUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = FwTheme.spacing.gutter, end = FwTheme.spacing.gutter, top = 20.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FwOverline(stringResource(R.string.trips_week, weekRange(week)), Modifier.weight(1f))
        Text(
            stringResource(
                R.string.trips_week_totals,
                formatKm(week.km),
                IndianNumberFormat.rupeesWhole(week.cost),
            ),
            style = FwTheme.type.caption,
            color = FwTheme.colors.ink2,
        )
    }
}

@Composable
private fun TripRowItem(row: TripRow, zone: ZoneId, onOpen: (String) -> Unit) {
    val trip = row.trip
    val time = Instant.ofEpochMilli(trip.startUtcMs).atZone(zone).toLocalTime()
    val meta = listOfNotNull(
        shortDate(trip.startUtcMs, zone),
        time.format(TIME_FORMAT),
        tripDuration(trip),
        purposeName(trip),
        row.vehicleName,
    ).joinToString(" · ")
    FwEntryRow(
        icon = TripLabels.purposeIcon(trip.purpose),
        tint = FwTheme.colors.ink,
        title = tripTitle(trip),
        meta = meta,
        amount = stringResource(R.string.trips_km, formatKm(trip.distanceKm)),
        chip = row.cost?.let { stringResource(R.string.trips_cost_est, IndianNumberFormat.rupeesWhole(it)) },
        onClick = { onOpen(trip.id) },
        modifier = Modifier.padding(horizontal = FwTheme.spacing.gutter),
    )
}

/** "13–19 Oct", or "29 Sept – 5 Oct" across months. */
private fun weekRange(week: TripWeekUi): String = if (week.start.month == week.end.month) {
    "${week.start.dayOfMonth}–${week.end.format(DAY_MONTH)}"
} else {
    "${week.start.format(DAY_MONTH)} – ${week.end.format(DAY_MONTH)}"
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private const val UNDO_WINDOW_MS = 10_000L

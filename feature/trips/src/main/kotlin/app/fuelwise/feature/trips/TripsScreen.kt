package app.fuelwise.feature.trips

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwEntryRow
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.ExportRange
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.ui.entry.shortDate
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class TripsActions(
    val onStart: () -> Unit = {},
    val onResume: (tripId: String) -> Unit = {},
    val onFinish: (tripId: String) -> Unit = {},
    val onExport: () -> Unit = {},
)

@Composable
internal fun TripsScreen(links: TripsLinks, startTrip: Boolean = false, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // What to do once location is granted: START, or the id of an interrupted trip to resume.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val run: (String) -> Unit = { action ->
        if (action == START) viewModel.startTrip() else viewModel.resumeInterrupted(action)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = pending
        pending = null
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            action?.let(run)
        } else {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.trips_location_needed)) }
        }
    }
    // While-in-use location only, asked when the user starts recording (PRD §9). Notifications ride along so the
    // ongoing trip notification shows; recording works without them.
    val withLocation: (String) -> Unit = { action ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            run(action)
        } else {
            pending = action
            permission.launch(locationPermissions())
        }
    }
    // Opened from Home's "Start trip": start once, not again on rotation or when coming back from the live screen.
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startTrip) {
        if (startTrip && !autoStarted) {
            autoStarted = true
            withLocation(START)
        }
    }
    var choosingRange by rememberSaveable { mutableStateOf(false) }
    var exportRange by rememberSaveable { mutableStateOf(ExportRange.ThisMonth) }
    val saveGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GPX_MIME)) { uri ->
        uri?.let { viewModel.exportRange(it, exportRange) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TripsEvent.GpxExported -> snackbar.showSnackbar(
                    when {
                        event.failed -> resources.getString(R.string.trips_gpx_failed)
                        event.saved == 0 -> resources.getString(R.string.trips_gpx_none)
                        else -> resources.getQuantityString(R.plurals.trips_gpx_saved_many, event.saved, event.saved)
                    },
                )

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

                TripsEvent.OpenLive -> links.onOpenLive()

                is TripsEvent.OpenTrip -> links.onOpenTrip(event.tripId)

                TripsEvent.TooShort -> snackbar.showSnackbar(resources.getString(R.string.trips_too_short))
            }
        }
    }
    Box(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        TripsContent(
            state,
            links,
            TripsActions(
                onStart = { withLocation(START) },
                onResume = withLocation,
                onFinish = viewModel::finishInterrupted,
                onExport = { choosingRange = true },
            ),
        )
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (choosingRange) {
        RangeSheet(onDismiss = { choosingRange = false }) { range ->
            choosingRange = false
            exportRange = range
            saveGpx.launch(gpxRangeFileName(LocalDate.now()))
        }
    }
}

/** Which trips go into one GPX file (PRD F-07.8: per trip or range). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSheet(onDismiss: () -> Unit, onPick: (ExportRange) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = FwTheme.spacing.m)) {
            Text(
                stringResource(R.string.trips_export_title),
                style = FwTheme.type.h3,
                color = FwTheme.colors.ink,
                modifier = Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
            )
            ExportRange.entries.forEach { range ->
                Text(
                    stringResource(rangeLabel(range)),
                    style = FwTheme.type.body,
                    color = FwTheme.colors.ink,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(range) }
                        .heightIn(min = 56.dp).padding(horizontal = FwTheme.spacing.gutter, vertical = 16.dp),
                )
            }
            Text(
                stringResource(R.string.trips_export_note),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
            )
        }
    }
}

private fun rangeLabel(range: ExportRange) = when (range) {
    ExportRange.ThisWeek -> R.string.trips_range_week
    ExportRange.ThisMonth -> R.string.trips_range_month
    ExportRange.LastThreeMonths -> R.string.trips_range_three_months
    ExportRange.All -> R.string.trips_range_all
}

/**
 * Trips (design.md screen 17): start a GPS trip or add one by hand, the trip recording now, trips a crash cut short,
 * then weekly groups with totals, newest first.
 */
@Composable
internal fun TripsContent(
    state: TripsUiState,
    links: TripsLinks,
    actions: TripsActions = TripsActions(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.trips_title),
            onBack = links.onBack,
            backContentDescription = stringResource(R.string.trips_back),
            actions = {
                if (state.weeks.isNotEmpty()) {
                    IconButton(onClick = actions.onExport) {
                        Icon(
                            painterResource(FwIcons.Download),
                            stringResource(R.string.trips_export_gpx),
                            tint = FwTheme.colors.ink,
                        )
                    }
                }
            },
        )
        if (state.loading) return@Column
        if (!state.hasVehicles) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FwEmptyState(
                    icon = FwIcons.Route,
                    headline = stringResource(R.string.trips_no_vehicles),
                    body = stringResource(R.string.trips_no_vehicles_body),
                )
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FwTheme.spacing.xl)) {
            item { StartButtons(state.recording, links, actions) }
            if (state.offerAutoTrips) item(key = "auto-offer") { AutoTripsCard(links.onAutoSetup) }
            items(state.interrupted, key = { "cut-${it.id}" }) { trip -> InterruptedCard(trip, zone, actions) }
            if (state.weeks.isEmpty()) {
                item {
                    FwEmptyState(
                        icon = FwIcons.Route,
                        headline = stringResource(R.string.trips_empty),
                        body = stringResource(R.string.trips_empty_body),
                        modifier = Modifier.padding(top = FwTheme.spacing.xl),
                    )
                }
            }
            state.weeks.forEach { week ->
                item(key = "week-${week.start}") { WeekHeader(week) }
                items(week.rows, key = { it.trip.id }) { row -> TripRowItem(row, zone, links.onOpenTrip) }
            }
        }
    }
}

/** "Start trip" (accent) and "Add manual trip"; while recording, the accent button leads back to the live screen. */
@Composable
private fun StartButtons(recording: RecordingBanner?, links: TripsLinks, actions: TripsActions) {
    Column(
        Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (recording == null) {
            FwPrimaryButton(
                stringResource(R.string.trips_start),
                actions.onStart,
                Modifier.fillMaxWidth(),
                leadingIcon = FwIcons.Route,
            )
        } else {
            val label = if (recording.paused) R.string.trips_paused_banner else R.string.trips_recording_banner
            FwPrimaryButton(
                stringResource(label, formatKm(recording.km)),
                links.onOpenLive,
                Modifier.fillMaxWidth(),
                leadingIcon = FwIcons.Route,
            )
        }
        FwSecondaryButton(
            stringResource(R.string.trips_add_manual),
            links.onAddManual,
            Modifier.fillMaxWidth(),
            leadingIcon = FwIcons.ManualTrip,
        )
    }
}

/** A quiet card leading to automatic trips setup (design.md screen 17 → 32). */
@Composable
private fun AutoTripsCard(onSetup: () -> Unit) {
    FwCard(Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = 6.dp), onClick = onSetup) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.trips_auto_card_title),
                    style = FwTheme.type.label,
                    color = FwTheme.colors.ink,
                )
                Text(
                    stringResource(R.string.trips_auto_card_body),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(painterResource(FwIcons.ChevronRight), null, tint = FwTheme.colors.ink3)
        }
    }
}

/** A GPS trip cut short by a crash, reboot or revoked permission (PRD F-07.3): resume it or finish it. */
@Composable
private fun InterruptedCard(trip: Trip, zone: ZoneId, actions: TripsActions) {
    val time = Instant.ofEpochMilli(trip.startUtcMs).atZone(zone)
    FwCard(Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = 6.dp)) {
        Text(stringResource(R.string.trips_interrupted_title), style = FwTheme.type.label, color = FwTheme.colors.ink)
        Text(
            stringResource(R.string.trips_interrupted_body, shortDate(trip.startUtcMs, zone), time.format(TIME_FORMAT)),
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink2,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            FwInkButton(stringResource(R.string.trips_resume), { actions.onResume(trip.id) }, Modifier.weight(1f))
            FwSecondaryButton(
                stringResource(R.string.trips_finish),
                { actions.onFinish(trip.id) },
                Modifier.weight(1f),
            )
        }
    }
}

private fun locationPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private const val START = "start"
private const val GPX_MIME = "application/gpx+xml"

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
        // Recorded on its own (PRD F-21.3): "auto" tag, design.md screen 17.
        if (trip.source == TripSource.Auto) stringResource(R.string.trips_auto_tag) else null,
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

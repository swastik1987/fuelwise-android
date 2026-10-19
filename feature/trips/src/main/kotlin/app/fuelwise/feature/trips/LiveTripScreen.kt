package app.fuelwise.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwMetricTile
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.map.FwRouteMap
import java.util.Locale
import kotlinx.coroutines.delay

internal data class LiveTripActions(
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onStop: () -> Unit = {},
)

@Composable
internal fun LiveTripScreen(links: TripsLinks, viewModel: LiveTripViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // Only while the screen is visible: with it hidden, fixes arrive in the normal 90 s batches.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.flush()
                delay(FLUSH_EVERY_MS)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LiveTripEvent.Finished -> links.onLiveFinished(event.tripId)

                LiveTripEvent.TooShort -> {
                    snackbar.showSnackbar(resources.getString(R.string.trips_too_short))
                    links.onLiveFinished(null)
                }
            }
        }
    }
    val now by produceState(System.currentTimeMillis(), state.recording?.paused) {
        while (true) {
            value = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    Box(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        LiveTripContent(state, now, links, LiveTripActions(viewModel::pause, viewModel::resume, viewModel::stop))
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Live trip (design.md screen 18): the route so far, and big numbers readable at arm's length. The trace is drawn
 * on a map that follows the newest fix.
 */
@Composable
internal fun LiveTripContent(state: LiveTripUiState, nowUtcMs: Long, links: TripsLinks, actions: LiveTripActions) {
    val recording = state.recording
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(
                when {
                    recording == null -> R.string.trips_live_title
                    recording.paused -> R.string.trips_live_paused
                    else -> R.string.trips_live_recording
                },
            ),
            onBack = links.onBack,
            backContentDescription = stringResource(R.string.trips_back),
        )
        if (state.loading) return@Column
        if (recording == null) {
            if (!state.stopping) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FwEmptyState(
                        icon = FwIcons.Route,
                        headline = stringResource(R.string.trips_live_none),
                        body = stringResource(R.string.trips_live_none_body),
                    )
                }
            }
            return@Column
        }
        LiveMap(recording.route, Modifier.weight(1f).fillMaxWidth().padding(horizontal = FwTheme.spacing.gutter))
        LivePanel(state, recording, nowUtcMs, actions)
    }
}

@Composable
private fun LivePanel(
    state: LiveTripUiState,
    recording: RecordingState.Active,
    nowUtcMs: Long,
    actions: LiveTripActions,
) {
    val km = IndianNumberFormat.decimal(recording.summary.distanceKm, 1)
    val kmLabel = stringResource(R.string.trips_km, km)
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(FwTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            // Read out when it changes, not on every recomposition.
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = kmLabel
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Text(
                km,
                style = FwTheme.type.displayXl.copy(fontFamily = FwTheme.type.mono.fontFamily),
                color = FwTheme.colors.ink,
            )
            Text(
                stringResource(R.string.trips_km_unit),
                style = FwTheme.type.h2,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FwMetricTile(
                overline = stringResource(R.string.trips_live_elapsed),
                value = clock(recording.elapsedMs(nowUtcMs)),
                modifier = Modifier.weight(1f),
            )
            FwMetricTile(
                overline = stringResource(R.string.trips_live_avg_speed),
                value = recording.summary.averageKmh?.let { IndianNumberFormat.decimal(it, 0) } ?: "—",
                unit = stringResource(R.string.trips_kmh),
                modifier = Modifier.weight(1f),
            )
            FwMetricTile(
                overline = stringResource(R.string.trips_stat_cost),
                value = state.estimate?.let(IndianNumberFormat::rupeesWhole) ?: "—",
                caption = stringResource(R.string.trips_est),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recording.paused) {
                FwSecondaryButton(stringResource(R.string.trips_live_resume), actions.onResume, Modifier.weight(1f))
            } else {
                FwSecondaryButton(stringResource(R.string.trips_live_pause), actions.onPause, Modifier.weight(1f))
            }
            FwPrimaryButton(
                stringResource(R.string.trips_live_stop),
                actions.onStop,
                Modifier.weight(1f),
                enabled = !state.stopping,
            )
        }
    }
}

/** The route so far on a map that follows the newest fix (design.md screen 18: live polyline in accent). */
@Composable
private fun LiveMap(route: List<TrackPoint>, modifier: Modifier) {
    Box(modifier.clip(FwTheme.radius.card), contentAlignment = Alignment.Center) {
        FwRouteMap(
            route,
            stringResource(R.string.trips_map_live),
            Modifier.fillMaxSize(),
            follow = true,
        )
        if (route.size < 2) {
            Text(
                stringResource(R.string.trips_live_waiting),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink,
                modifier = Modifier.background(FwTheme.colors.surface, FwTheme.radius.pill)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** "38:12", or "1:05:09" past an hour. */
private fun clock(ms: Long): String {
    val total = ms / MS_PER_S
    val h = total / SECONDS_PER_HOUR
    val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

private const val FLUSH_EVERY_MS = 10_000L
private const val TICK_MS = 1_000L
private const val MS_PER_S = 1_000L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L

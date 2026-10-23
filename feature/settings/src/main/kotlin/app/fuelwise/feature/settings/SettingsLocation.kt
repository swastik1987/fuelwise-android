package app.fuelwise.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.component.FwChoiceSheet
import app.fuelwise.core.designsystem.component.FwSettingsRow
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.AutoTripPause
import app.fuelwise.core.domain.trip.RouteRetention
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Settings → Location & trips (design.md screen 29; PRD F-21.3, F-21.6).

/**
 * Location & trips (design.md screen 29, PRD F-21.3). Off leads to setup; on opens pause and turn-off choices. Route
 * retention and private places arrive with F-21.6.
 */
@Composable
internal fun LocationSection(
    state: SettingsUiState,
    chrome: SettingsChrome,
    actions: SettingsActions,
    open: (SettingsSheet) -> Unit,
) {
    Header(R.string.settings_section_location)
    val auto = state.autoTrips
    if (auto != null && chrome.showAutoTrips) {
        FwSettingsRow(
            stringResource(R.string.settings_auto_trips),
            value = autoTripsValue(auto),
            onClick = { if (auto.enabled) open(SettingsSheet.AutoTrips) else actions.onAutoTripSetup() },
        )
        if (auto.enabled) {
            FwSettingsRow(
                stringResource(R.string.settings_battery_saver),
                value = stringResource(R.string.settings_battery_saver_value),
                onClick = actions.onBatterySaver,
            )
        }
    }
    // PRD F-21.6: how long routes stay, places trimmed from route ends, and a separate delete.
    FwSettingsRow(
        stringResource(R.string.settings_route_retention),
        value = stringResource(retentionLabel(state.routeRetention)),
        onClick = { open(SettingsSheet.Retention) },
    )
    FwSettingsRow(
        stringResource(R.string.settings_private_places),
        value = if (state.privatePlaceCount == 0) {
            stringResource(R.string.settings_private_places_none)
        } else {
            pluralStringResource(
                R.plurals.settings_private_places_count,
                state.privatePlaceCount,
                state.privatePlaceCount,
            )
        },
        onClick = actions.onPrivatePlaces,
    )
    FwSettingsRow(
        stringResource(R.string.settings_delete_location),
        titleColor = FwTheme.colors.negative,
        onClick = { open(SettingsSheet.DeleteLocation) },
    )
}

internal fun retentionLabel(retention: RouteRetention): Int = when (retention) {
    RouteRetention.Days30 -> R.string.settings_route_retention_30
    RouteRetention.Days90 -> R.string.settings_route_retention_90
    RouteRetention.Year -> R.string.settings_route_retention_year
    RouteRetention.Forever -> R.string.settings_route_retention_forever
}

@Composable
private fun autoTripsValue(auto: AutoTripsUi): String {
    val until = auto.pausedUntilUtcMs
    return when {
        !auto.enabled -> stringResource(R.string.settings_auto_trips_off)
        until == Long.MAX_VALUE -> stringResource(R.string.settings_auto_trips_paused)
        until != null -> stringResource(R.string.settings_auto_trips_paused_until, pauseEnd(until))
        auto.oneTap -> stringResource(R.string.settings_auto_trips_one_tap)
        else -> stringResource(R.string.settings_auto_trips_on)
    }
}

/** "18:00" today, otherwise "24 Oct, 05:00". */
private fun pauseEnd(utcMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(utcMs).atZone(zone)
    val today = LocalDate.now(zone)
    return if (at.toLocalDate() == today) at.format(PAUSE_TIME) else at.format(PAUSE_DAY_TIME)
}

private val PAUSE_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val PAUSE_DAY_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

/** Pause for a while, resume, or turn automatic trips off. */
@Composable
internal fun AutoTripsSheet(auto: AutoTripsUi?, actions: SettingsActions, onDismiss: () -> Unit) {
    val paused = auto?.pausedUntilUtcMs != null
    val choices = buildList {
        if (paused) add(R.string.settings_auto_trips_resume to actions.onResumeAutoTrips)
        add(R.string.settings_auto_trips_pause_hour to { actions.onPauseAutoTrips(AutoTripPause.OneHour) })
        add(R.string.settings_auto_trips_pause_tomorrow to { actions.onPauseAutoTrips(AutoTripPause.UntilTomorrow) })
        add(R.string.settings_auto_trips_pause_until_on to { actions.onPauseAutoTrips(AutoTripPause.UntilTurnedOn) })
        add(R.string.settings_auto_trips_turn_off to actions.onTurnOffAutoTrips)
    }
    FwChoiceSheet(
        title = stringResource(R.string.settings_auto_trips),
        options = choices.map { stringResource(it.first) },
        selectedIndex = -1,
        onSelect = {
            onDismiss()
            choices[it].second()
        },
        onDismiss = onDismiss,
    )
}

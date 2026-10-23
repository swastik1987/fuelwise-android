package app.fuelwise.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.component.FwChoiceSheet
import app.fuelwise.core.designsystem.component.FwStatus
import app.fuelwise.core.designsystem.component.FwStatusPill
import app.fuelwise.core.domain.trip.AutoTripPause
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The pill's state and what its sheet can do (PRD F-21.3 controls: "Home toggle"). */
data class AutoTripControls(
    val pill: AutoTripPill? = null,
    val onPause: (AutoTripPause) -> Unit = {},
    val onResume: () -> Unit = {},
    /** Amber "Blocked by battery saver" leads to the fix screen (design.md screen 33). */
    val onFixBattery: () -> Unit = {},
)

/**
 * "Auto trips on" (green), "Paused until 18:00" (grey) or "Blocked by battery saver" (amber) in Home's top bar. A tap
 * opens the pause choices, or the battery-saver fix when blocked.
 */
@Composable
internal fun AutoTripsStatus(pill: AutoTripPill, onClick: () -> Unit) {
    val until = pill.pausedUntilUtcMs
    val label = when {
        until == null && pill.blocked -> stringResource(R.string.home_auto_blocked)
        until == Long.MAX_VALUE -> stringResource(R.string.home_auto_paused)
        until != null -> stringResource(R.string.home_auto_paused_until, pauseEnd(until))
        pill.oneTap -> stringResource(R.string.home_auto_one_tap)
        else -> stringResource(R.string.home_auto_on)
    }
    val status = when {
        until != null -> FwStatus.Neutral
        pill.blocked -> FwStatus.Warning
        else -> FwStatus.Positive
    }
    FwStatusPill(label, status, onClick)
}

/** Pause for 1 hour, until tomorrow, or until turned back on; resume when paused. */
@Composable
internal fun AutoTripsPauseSheet(controls: AutoTripControls, onDismiss: () -> Unit) {
    val paused = controls.pill?.pausedUntilUtcMs != null
    val choices = buildList {
        if (paused) add(R.string.home_auto_resume to controls.onResume)
        add(R.string.home_auto_pause_hour to { controls.onPause(AutoTripPause.OneHour) })
        add(R.string.home_auto_pause_tomorrow to { controls.onPause(AutoTripPause.UntilTomorrow) })
        add(R.string.home_auto_pause_until_on to { controls.onPause(AutoTripPause.UntilTurnedOn) })
    }
    FwChoiceSheet(
        title = stringResource(R.string.home_auto_sheet_title),
        options = choices.map { stringResource(it.first) },
        selectedIndex = -1,
        onSelect = {
            onDismiss()
            choices[it].second()
        },
        onDismiss = onDismiss,
    )
}

/** "18:00" today, otherwise "24 Oct, 05:00". */
private fun pauseEnd(utcMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(utcMs).atZone(zone)
    return if (at.toLocalDate() == LocalDate.now(zone)) at.format(TIME) else at.format(DAY_TIME)
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

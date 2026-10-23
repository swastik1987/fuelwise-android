package app.fuelwise.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.location.TripRecorder
import app.fuelwise.core.notifications.NotificationRoutes
import app.fuelwise.core.ui.format.IndianNumberFormat
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Quick Settings tile "Log fuel" (PRD F-16): opens the fill-up sheet. */
class LogFuelTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_log_fuel)
            updateTile()
        }
    }

    override fun onClick() = openApp(WidgetLinks.open(this, NotificationRoutes.OPEN_ADD_FUEL), REQUEST_LOG_FUEL)
}

/**
 * Quick Settings tile "Start trip" (PRD F-16). Android doesn't list a tile tap among the ways a background app may
 * start a location service, so the tile opens Trips, which asks for location if needed and starts the trip. While a
 * trip records the tile is on and shows the distance; tapping it then opens the live trip.
 */
@AndroidEntryPoint
class StartTripTileService : TileService() {
    @Inject lateinit var recorder: TripRecorder

    override fun onStartListening() {
        val active = recorder.state.value as? RecordingState.Active
        qsTile?.apply {
            state = if (active != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_start_trip)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = active?.let {
                    getString(R.string.tile_trip_recording, IndianNumberFormat.decimal(it.summary.distanceKm, 1))
                }
            }
            updateTile()
        }
    }

    override fun onClick() {
        val target = if (recorder.state.value is RecordingState.Active) {
            NotificationRoutes.OPEN_LIVE_TRIP
        } else {
            NotificationRoutes.OPEN_START_TRIP
        }
        openApp(WidgetLinks.open(this, target), REQUEST_START_TRIP)
    }
}

/**
 * Quick Settings tile "Pause automatic trips" (PRD F-16, F-21.3). On while automatic trips are watching for drives;
 * a tap pauses them until turned back on, another tap resumes. Before setup, a tap opens the setup screen instead.
 */
@AndroidEntryPoint
class AutoTripsTileService : TileService() {
    @Inject lateinit var settings: AutoTripSettingsRepository

    @Inject lateinit var clock: Clock

    // Reads and writes one preference; the tile service lives only while the shade shows it.
    private val scope = MainScope()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        scope.launch {
            val s = settings.settings.first()
            val watching = s.isWatching(clock.millis())
            qsTile?.apply {
                state = if (s.enabled && watching) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = getString(R.string.tile_auto_trips)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subtitle = getString(
                        when {
                            !s.enabled -> R.string.tile_auto_trips_off
                            watching -> R.string.tile_auto_trips_on
                            else -> R.string.tile_auto_trips_paused
                        },
                    )
                }
                updateTile()
            }
        }
    }

    override fun onClick() {
        scope.launch {
            val s = settings.settings.first()
            when {
                !s.available -> Unit

                !s.enabled -> openApp(
                    WidgetLinks.open(this@AutoTripsTileService, NotificationRoutes.OPEN_AUTO_TRIP_SETUP),
                    REQUEST_AUTO_TRIPS,
                )

                s.isWatching(clock.millis()) -> settings.pauseUntil(Long.MAX_VALUE)

                else -> settings.pauseUntil(null)
            }
            onStartListening()
        }
    }
}

/** Opens the app and closes the shade; on the lock screen, after the user unlocks. */
private fun TileService.openApp(intent: Intent, requestCode: Int) {
    val open = Runnable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }
    if (isLocked) unlockAndRun(open) else open.run()
}

// Android 13 and older only take an Intent here; 14+ uses the PendingIntent overload above.
@SuppressLint("StartActivityAndCollapseDeprecated")
@Suppress("DEPRECATION")
private fun TileService.startActivityAndCollapseLegacy(intent: Intent) = startActivityAndCollapse(intent)

private const val REQUEST_LOG_FUEL = 16_001
private const val REQUEST_START_TRIP = 16_002
private const val REQUEST_AUTO_TRIPS = 16_003

package app.fuelwise.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.notifications.NotificationChannels
import app.fuelwise.core.notifications.NotificationRoutes
import app.fuelwise.core.ui.format.IndianNumberFormat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and visible while a GPS trip records (PRD F-07.2): foreground service of type
 * `location`, started while the app is on screen, with an ongoing notification showing distance and time and
 * Pause / Stop actions. The recording itself lives in [TripRecorder]. Not sticky: after a crash or reboot the app
 * offers to resume or finish instead (PRD F-07.3), since a background restart may not read location.
 */
@AndroidEntryPoint
class TripRecordingService : Service() {
    @Inject lateinit var recorder: TripRecorder

    @Inject lateinit var clock: Clock

    @Inject lateinit var startTrip: StartTripForSelectedVehicle

    @Inject lateinit var autoTrips: AutoTrips

    @Inject lateinit var vehicles: VehicleRepository

    @Inject
    @RecorderScope
    lateinit var scope: CoroutineScope

    private var watch: Job? = null

    // The automatic-trip notification names the vehicle; looked up once per trip.
    private var vehicleName: Pair<String, String>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start must promptly show the notification, whatever the action.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(recorder.state.value),
                // The type exists from Android 10; before that the manifest declaration is all there is.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (_: SecurityException) {
            // Location was revoked after a widget last drew its Start button: nothing can record, so don't start.
            stopSelf()
            return START_NOT_STICKY
        }
        val begin: (suspend () -> Unit)? = when (intent?.action) {
            ACTION_START -> suspend { startTrip() }

            // Driving detection (PRD F-21.3).
            ACTION_START_AUTO -> {
                val car = intent.getStringExtra(EXTRA_VEHICLE_ID)
                suspend { autoTrips.startAutomaticTrip(car) }
            }

            else -> null
        }
        when (intent?.action) {
            ACTION_PAUSE -> recorder.pause()
            ACTION_RESUME -> recorder.resume()
            ACTION_STOP -> scope.launch { recorder.stop() }
            ACTION_NOT_MY_DRIVE -> scope.launch { recorder.discardCurrent() }
        }
        if (watch == null) {
            watch = scope.launch {
                // A widget or automatic start begins Idle: start first, or the watch would stop the service at once.
                begin?.invoke()
                recorder.state.collectLatest { state ->
                    if (state is RecordingState.Idle) {
                        ServiceCompat.stopForeground(this@TripRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        post(state)
                    }
                }
            }
        } else if (begin != null) {
            scope.launch { begin() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watch?.cancel()
        watch = null
        super.onDestroy()
    }

    // POST_NOTIFICATIONS is checked just below; lint doesn't follow the check in this class.
    @SuppressLint("MissingPermission")
    private suspend fun post(state: RecordingState) {
        val active = state as? RecordingState.Active
        if (active?.source == TripSource.Auto && vehicleName?.first != active.tripId) {
            vehicleName = active.tripId to vehicles.observeVehicle(active.vehicleId).first()?.name.orEmpty()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(state))
        } catch (_: SecurityException) {
            // No notification permission: the service still runs; the system shows it in its task manager.
        }
    }

    private fun notification(state: RecordingState): Notification {
        val active = state as? RecordingState.Active
        val paused = active?.paused == true
        val auto = active?.source == TripSource.Auto
        val km = IndianNumberFormat.decimal(active?.summary?.distanceKm ?: 0.0, 1)
        val title = when {
            paused -> getString(R.string.trip_notif_paused)

            // Minimal, on its own channel (design.md screen 31): "Recording drive · Safari".
            auto -> vehicleName?.takeIf { it.first == active.tripId && it.second.isNotEmpty() }
                ?.let { getString(R.string.auto_notif_recording, it.second) }
                ?: getString(R.string.auto_notif_recording_no_vehicle)

            else -> getString(R.string.trip_notif_recording)
        }
        val channel = if (auto) NotificationChannels.AUTOMATIC_TRIPS else NotificationChannels.TRIP_RECORDING
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(FwIcons.Route)
            .setContentTitle(title)
            .setContentText(getString(R.string.trip_notif_distance, km))
            .setContentIntent(openLiveTrip())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (active != null && !paused) {
            // The chronometer ticks on its own, so the notification needn't be updated every second.
            val now = clock.millis()
            builder.setUsesChronometer(true).setShowWhen(true).setWhen(now - active.elapsedMs(now))
        } else {
            builder.setShowWhen(false)
        }
        if (paused) {
            builder.addAction(0, getString(R.string.trip_notif_resume), action(ACTION_RESUME, REQUEST_RESUME))
        } else {
            builder.addAction(0, getString(R.string.trip_notif_pause), action(ACTION_PAUSE, REQUEST_PAUSE))
        }
        if (auto) {
            // An automatic trip stops by itself; the passenger case is the one that needs a button.
            builder.addAction(
                0,
                getString(R.string.auto_notif_not_my_drive),
                action(ACTION_NOT_MY_DRIVE, REQUEST_NOT_MY_DRIVE),
            )
        } else {
            builder.addAction(0, getString(R.string.trip_notif_stop), action(ACTION_STOP, REQUEST_STOP))
        }
        return builder.build()
    }

    private fun action(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, TripRecordingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openLiveTrip(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.putExtra(NotificationRoutes.EXTRA_OPEN, NotificationRoutes.OPEN_LIVE_TRIP)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: Intent()
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal companion object {
        /** From a widget tap (PRD F-16): start a trip for the selected vehicle. */
        const val ACTION_START = "app.fuelwise.trip.START"
        const val ACTION_PAUSE = "app.fuelwise.trip.PAUSE"
        const val ACTION_RESUME = "app.fuelwise.trip.RESUME"
        const val ACTION_STOP = "app.fuelwise.trip.STOP"

        /** From driving detection (PRD F-21.3): start an automatic trip. */
        const val ACTION_START_AUTO = "app.fuelwise.trip.START_AUTO"

        /** With [ACTION_START_AUTO]: the vehicle whose car Bluetooth connected. */
        const val EXTRA_VEHICLE_ID = "app.fuelwise.trip.VEHICLE_ID"

        /** "Not my drive" on an automatic trip: discard it. */
        const val ACTION_NOT_MY_DRIVE = "app.fuelwise.trip.NOT_MY_DRIVE"
        private const val NOTIFICATION_ID = 7_001
        private const val REQUEST_OPEN = 7_100
        private const val REQUEST_PAUSE = 7_101
        private const val REQUEST_RESUME = 7_102
        private const val REQUEST_STOP = 7_103
        private const val REQUEST_NOT_MY_DRIVE = 7_105
    }
}

/** Starts [TripRecordingService]; called from the app while it's on screen, which Android 14 requires. */
internal class ServiceRecordingHost @Inject constructor(@param:ApplicationContext private val context: Context) :
    RecordingHost {
    override fun startForegroundRecording() {
        ContextCompat.startForegroundService(context, Intent(context, TripRecordingService::class.java))
    }
}

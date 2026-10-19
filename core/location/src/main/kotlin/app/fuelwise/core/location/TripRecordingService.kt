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
import app.fuelwise.core.designsystem.icon.FwIcons
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

    @Inject
    @RecorderScope
    lateinit var scope: CoroutineScope

    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start must promptly show the notification, whatever the action.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(recorder.state.value),
            // The type exists from Android 10; before that the manifest declaration is all there is.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        when (intent?.action) {
            ACTION_PAUSE -> recorder.pause()
            ACTION_RESUME -> recorder.resume()
            ACTION_STOP -> scope.launch { recorder.stop() }
        }
        if (watch == null) {
            watch = scope.launch {
                recorder.state.collectLatest { state ->
                    if (state is RecordingState.Idle) {
                        ServiceCompat.stopForeground(this@TripRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        post(state)
                    }
                }
            }
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
    private fun post(state: RecordingState) {
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
        val km = IndianNumberFormat.decimal(active?.summary?.distanceKm ?: 0.0, 1)
        val builder = NotificationCompat.Builder(this, NotificationChannels.TRIP_RECORDING)
            .setSmallIcon(FwIcons.Route)
            .setContentTitle(getString(if (paused) R.string.trip_notif_paused else R.string.trip_notif_recording))
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
        builder.addAction(0, getString(R.string.trip_notif_stop), action(ACTION_STOP, REQUEST_STOP))
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
        const val ACTION_PAUSE = "app.fuelwise.trip.PAUSE"
        const val ACTION_RESUME = "app.fuelwise.trip.RESUME"
        const val ACTION_STOP = "app.fuelwise.trip.STOP"
        private const val NOTIFICATION_ID = 7_001
        private const val REQUEST_OPEN = 7_100
        private const val REQUEST_PAUSE = 7_101
        private const val REQUEST_RESUME = 7_102
        private const val REQUEST_STOP = 7_103
    }
}

/** Starts [TripRecordingService]; called from the app while it's on screen, which Android 14 requires. */
internal class ServiceRecordingHost @Inject constructor(@param:ApplicationContext private val context: Context) :
    RecordingHost {
    override fun startForegroundRecording() {
        ContextCompat.startForegroundService(context, Intent(context, TripRecordingService::class.java))
    }
}

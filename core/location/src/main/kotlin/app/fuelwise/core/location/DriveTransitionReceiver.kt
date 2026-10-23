package app.fuelwise.core.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Activity transitions from Play services (PRD F-21.3 start and stop signals). Not exported. */
@AndroidEntryPoint
class DriveTransitionReceiver : BroadcastReceiver() {
    @Inject lateinit var autoTrips: AutoTrips

    @Inject
    @Dispatcher(FwDispatcher.IO)
    lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRANSITION || !ActivityTransitionResult.hasResult(intent)) return
        // Only the latest one matters: several can arrive together after the phone wakes.
        val last =
            ActivityTransitionResult.extractResult(intent)?.transitionEvents?.maxByOrNull { it.elapsedRealTimeNanos }
                ?: return
        val driving = last.activityType == DetectedActivity.IN_VEHICLE &&
            last.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + io).launch {
            try {
                if (driving) autoTrips.onDriving() else autoTrips.onLeftVehicle()
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION_TRANSITION = "app.fuelwise.trip.ACTIVITY_TRANSITION"
    }
}

/** Activity-transition registrations don't survive a reboot or an app update (PRD F-21.3); this restores them. */
@AndroidEntryPoint
class DriveDetectionRestoreReceiver : BroadcastReceiver() {
    @Inject lateinit var autoTrips: AutoTrips

    @Inject
    @Dispatcher(FwDispatcher.IO)
    lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + io).launch {
            try {
                autoTrips.syncRegistration()
            } finally {
                pending.finish()
            }
        }
    }
}

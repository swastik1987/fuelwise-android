package app.fuelwise.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Android's activity detection for automatic trips (PRD F-21.3, the start and stop signals). Registering asks for
 * activity transitions only, not location: detection runs in Play services and wakes the app on a transition.
 */
interface DriveDetection {
    /** Physical activity (Android 10+; granted at install before that). */
    val hasPermission: Boolean

    /** "Allow all the time": without it a drive asks "Start trip?" instead of recording on its own. */
    val hasBackgroundLocation: Boolean

    fun register()

    fun unregister()
}

internal class ActivityTransitionDriveDetection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DriveDetection {
    private val client by lazy { ActivityRecognition.getClient(context) }

    override val hasPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACTIVITY_RECOGNITION)

    override val hasBackgroundLocation: Boolean
        get() = granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                )

    // The permission is checked first; lint doesn't follow the check through hasPermission.
    @SuppressLint("MissingPermission")
    override fun register() {
        if (!hasPermission) return
        val request = ActivityTransitionRequest(
            listOf(
                transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
                transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT),
                // Walking away is the clearest "left the vehicle", and often arrives before the vehicle exit.
                transition(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            ),
        )
        try {
            client.requestActivityTransitionUpdates(request, pendingIntent())
        } catch (_: SecurityException) {
            // Revoked between the check and the request: nothing to register.
        }
    }

    @SuppressLint("MissingPermission")
    override fun unregister() {
        try {
            client.removeActivityTransitionUpdates(pendingIntent())
        } catch (_: SecurityException) {
            // Without the permission there is nothing registered to remove.
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun transition(activity: Int, type: Int) =
        ActivityTransition.Builder().setActivityType(activity).setActivityTransition(type).build()

    // Mutable: Play services adds the transition result to it. Explicit, so nothing else can receive it.
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_TRANSITIONS,
        Intent(context, DriveTransitionReceiver::class.java).setAction(DriveTransitionReceiver.ACTION_TRANSITION),
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
    )

    private companion object {
        const val REQUEST_TRANSITIONS = 7_200
    }
}

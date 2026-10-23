package app.fuelwise.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.notifications.NotificationChannels
import app.fuelwise.core.notifications.NotificationRoutes
import app.fuelwise.core.ui.format.IndianNumberFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What the recorder asks of the user once a trip is saved. */
interface TripPrompts {
    /** "Trip saved · 23.4 km. Update odometer" (PRD F-07.6). */
    fun odometerAfterTrip(tripId: String, distanceKm: Double)

    /**
     * "Driving? Tap to start a trip" when automatic trips can't record on their own (no "Allow all the time", or
     * one-tap chosen; design.md screen 32). Tapping opens the app, which starts an ordinary GPS trip.
     */
    fun startTripPrompt()
}

/**
 * The post-trip odometer prompt as a notification with an "Update odometer" action; tapping either opens the trip
 * with its single-field odometer sheet. No floating overlay (PRD F-07.6). Without the notification permission the
 * trip detail screen offers the same sheet.
 */
internal class NotificationTripPrompts @Inject constructor(@param:ApplicationContext private val context: Context) :
    TripPrompts {
    // POST_NOTIFICATIONS is checked just below; lint doesn't follow the check in this class.
    @SuppressLint("MissingPermission")
    override fun odometerAfterTrip(tripId: String, distanceKm: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val id = tripId.hashCode() and Int.MAX_VALUE / 2
        val open = openOdometer(tripId, id)
        val notification = NotificationCompat.Builder(context, NotificationChannels.TRIP_RECORDING)
            .setSmallIcon(FwIcons.Route)
            .setContentTitle(context.getString(R.string.trip_notif_saved, IndianNumberFormat.decimal(distanceKm, 1)))
            .setContentText(context.getString(R.string.trip_notif_odometer_body))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.trip_notif_update_odometer), open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Revoked between the check and the post: the trip detail still offers the sheet.
        }
    }

    @SuppressLint("MissingPermission")
    override fun startTripPrompt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(NotificationRoutes.EXTRA_OPEN, NotificationRoutes.OPEN_START_TRIP)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: Intent()
        val open = PendingIntent.getActivity(
            context,
            REQUEST_START_TRIP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.AUTOMATIC_TRIPS)
            .setSmallIcon(FwIcons.Route)
            .setContentTitle(context.getString(R.string.auto_notif_start_title))
            .setContentText(context.getString(R.string.auto_notif_start_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            // Gone by itself if ignored: a stale "Start trip?" after the drive is noise.
            .setTimeoutAfter(START_PROMPT_TIMEOUT_MS)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(START_PROMPT_ID, notification)
        } catch (_: SecurityException) {
            // Revoked between the check and the post.
        }
    }

    private fun openOdometer(tripId: String, requestCode: Int): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(NotificationRoutes.EXTRA_OPEN, NotificationRoutes.OPEN_TRIP_ODOMETER)
            ?.putExtra(NotificationRoutes.EXTRA_ARG, tripId)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val START_PROMPT_ID = 7_002
        const val REQUEST_START_TRIP = 7_104
        const val START_PROMPT_TIMEOUT_MS = 30 * 60 * 1_000L
    }
}

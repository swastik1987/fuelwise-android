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
}

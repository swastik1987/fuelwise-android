package app.fuelwise.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.format.IndianNumberFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToLong

/** Where a notification tap lands; MainActivity reads [EXTRA_OPEN]. */
object NotificationRoutes {
    const val EXTRA_OPEN = "app.fuelwise.extra.OPEN"

    /** The record the screen is for, e.g. a trip id. */
    const val EXTRA_ARG = "app.fuelwise.extra.ARG"
    const val OPEN_REMINDERS = "reminders"
    const val OPEN_RECAP = "recap"
    const val OPEN_FASTAG = "fastag"
    const val OPEN_LIVE_TRIP = "live_trip"

    /** A trip's detail with the odometer sheet open (PRD F-07.6); [EXTRA_ARG] is the trip id. */
    const val OPEN_TRIP_ODOMETER = "trip_odometer"
}

/** Channel ids other modules post to (the trip recorder in core:location). */
object NotificationChannels {
    const val TRIP_RECORDING = "trip_recording"
}

/**
 * Channels and notifications (core:notifications). Posting is skipped quietly without the POST_NOTIFICATIONS
 * grant; reminders still show in the app with a banner (PRD F-11 AC).
 */
@Singleton
class Notifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
                .apply { description = context.getString(R.string.channel_reminders_description) },
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INSIGHTS,
                context.getString(R.string.channel_insights),
                NotificationManager.IMPORTANCE_LOW,
            )
                .apply { description = context.getString(R.string.channel_insights_description) },
        )
        // Low importance: an ongoing, silent status while a trip records (PRD F-07.2, F-16 channels).
        system.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.TRIP_RECORDING,
                context.getString(R.string.channel_trip_recording),
                NotificationManager.IMPORTANCE_LOW,
            )
                .apply { description = context.getString(R.string.channel_trip_recording_description) },
        )
    }

    val canPost: Boolean
        get() = manager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                )

    fun reminder(status: ReminderStatus, vehicleName: String) {
        val r = status.reminder
        val title = r.title ?: context.getString(CareLabels.reminder(r.kind))
        val body = when {
            status.daysLeft != null && status.daysLeft!! < 0 ->
                context.resources.getQuantityString(
                    R.plurals.notif_overdue_days,
                    abs(status.daysLeft!!).toInt(),
                    vehicleName,
                    abs(status.daysLeft!!).toInt(),
                )

            status.daysLeft == 0L -> context.getString(R.string.notif_due_today, vehicleName)

            status.daysLeft != null -> context.resources.getQuantityString(
                R.plurals.notif_due_in_days,
                status.daysLeft!!.toInt(),
                vehicleName,
                status.daysLeft!!.toInt(),
            )

            status.kmLeft != null && status.kmLeft!! < 0 ->
                context.getString(
                    R.string.notif_overdue_km,
                    vehicleName,
                    IndianNumberFormat.group(abs(status.kmLeft!!).roundToLong()),
                )

            else -> context.getString(
                R.string.notif_due_in_km,
                vehicleName,
                IndianNumberFormat.group(
                    (
                        status.kmLeft
                            ?: 0.0
                        ).roundToLong(),
                ),
            )
        }
        val id = notificationId(r.id)
        val builder = base(CHANNEL_REMINDERS, title, body, open(NotificationRoutes.OPEN_REMINDERS, id))
            .addAction(
                0,
                context.getString(R.string.notif_action_done),
                action(ReminderActionReceiver.ACTION_DONE, r.id, id),
            )
            .addAction(
                0,
                context.getString(R.string.notif_action_snooze),
                action(ReminderActionReceiver.ACTION_SNOOZE, r.id, id),
            )
        post(id, builder)
    }

    fun recap(monthName: String) {
        post(
            RECAP_ID,
            base(
                CHANNEL_INSIGHTS,
                context.getString(R.string.notif_recap_title, monthName),
                context.getString(R.string.notif_recap_body),
                open(NotificationRoutes.OPEN_RECAP, RECAP_ID),
            ),
        )
    }

    fun fastagLow(vehicleName: String, balance: String) {
        val id = notificationId("fastag:$vehicleName")
        post(
            id,
            base(
                CHANNEL_REMINDERS,
                context.getString(R.string.notif_fastag_title),
                context.getString(R.string.notif_fastag_body, vehicleName, balance),
                open(NotificationRoutes.OPEN_FASTAG, id),
            ),
        )
    }

    fun cancelReminder(reminderId: String) = manager.cancel(notificationId(reminderId))

    private fun base(channel: String, title: String, body: String, tap: PendingIntent) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(FwIcons.Notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tap)
            .setAutoCancel(true)

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: the in-app banner covers it.
        }
    }

    private fun open(route: String, requestCode: Int): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(NotificationRoutes.EXTRA_OPEN, route)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(action: String, reminderId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
        val code = notificationId * 2 + if (action == ReminderActionReceiver.ACTION_DONE) 0 else 1
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(key: String): Int = key.hashCode() and Int.MAX_VALUE / 2

    private companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_INSIGHTS = "insights"
        const val RECAP_ID = 1
    }
}

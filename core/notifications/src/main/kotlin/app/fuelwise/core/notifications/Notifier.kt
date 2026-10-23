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
import app.fuelwise.core.domain.prices.PriceChange
import app.fuelwise.core.domain.reminder.ReminderCost
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.price.PriceLabels
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

    /** A notification to clear on open: an action button, unlike a tap on the notification, leaves it up. */
    const val EXTRA_DISMISS = "app.fuelwise.extra.DISMISS"
    const val OPEN_REMINDERS = "reminders"
    const val OPEN_RECAP = "recap"
    const val OPEN_FASTAG = "fastag"
    const val OPEN_LIVE_TRIP = "live_trip"

    /** A trip's detail with the odometer sheet open (PRD F-07.6); [EXTRA_ARG] is the trip id. */
    const val OPEN_TRIP_ODOMETER = "trip_odometer"

    /** City fuel prices (PRD F-09), from a price alert. */
    const val OPEN_PRICES = "prices"

    // Widgets, Quick Settings tiles and app shortcuts (PRD F-16) open the app the same way.

    /** The fill-up sheet; [EXTRA_ARG] may name the vehicle (an EV for "Charge"). */
    const val OPEN_ADD_FUEL = "add_fuel"

    /** The fill-up sheet pre-filled from a fill-up prompt (PRD F-21.4); [EXTRA_ARG] is `FillUpPrefill.encode()`. */
    const val OPEN_ADD_FUEL_PROMPT = "add_fuel_prompt"
    const val OPEN_ADD_EXPENSE = "add_expense"

    /** The expense sheet after a reminder's "Done" (PRD F-11.4); [EXTRA_ARG] is `ReminderCost.encode()`. */
    const val OPEN_ADD_REMINDER_COST = "add_reminder_cost"

    /** A manual trip, as from the in-app quick-add sheet. */
    const val OPEN_ADD_TRIP = "add_trip"

    /** The Trips screen starting a GPS trip, asking for location first if needed. */
    const val OPEN_START_TRIP = "start_trip"

    /** Automatic trips setup (PRD F-21.3), from the Quick Settings tile before it's set up. */
    const val OPEN_AUTO_TRIP_SETUP = "auto_trip_setup"
    const val OPEN_DOCUMENTS = "documents"

    /** Scan pump meter (PRD F-15). */
    const val OPEN_SCAN = "scan"

    /** The Insights tab, from the Monday weekly digest. */
    const val OPEN_INSIGHTS = "insights"
}

/** Channel ids other modules use: the trip recorder in core:location posts, Settings links to the system screens. */
object NotificationChannels {
    const val TRIP_RECORDING = "trip_recording"

    /** Automatic trips (PRD F-21.3, F-16): the quiet ongoing drive and the one-tap "Start trip?" prompt. */
    const val AUTOMATIC_TRIPS = "automatic_trips"

    /** "Filled up at …? Tap to log." after a drive (PRD F-21.4, F-16). */
    const val FILL_UP_PROMPTS = "fill_up_prompts"
    const val REMINDERS = "reminders"
    const val INSIGHTS = "insights"
    const val PRICE_ALERTS = "price_alerts"
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
        // Low importance: no sound while the user is driving (PRD F-21.3).
        system.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.AUTOMATIC_TRIPS,
                context.getString(R.string.channel_automatic_trips),
                NotificationManager.IMPORTANCE_LOW,
            )
                .apply { description = context.getString(R.string.channel_automatic_trips_description) },
        )
        // Default importance: a prompt the user acts on, once per drive at most (PRD F-21.4).
        system.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.FILL_UP_PROMPTS,
                context.getString(R.string.channel_fill_up_prompts),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
                .apply { description = context.getString(R.string.channel_fill_up_prompts_description) },
        )
        // Opt-in in the app (PRD F-09.5); low importance, a price move is never urgent.
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PRICE_ALERTS,
                context.getString(R.string.channel_price_alerts),
                NotificationManager.IMPORTANCE_LOW,
            )
                .apply { description = context.getString(R.string.channel_price_alerts_description) },
        )
    }

    /**
     * "Petrol in Bengaluru up ₹1.20/L today." (design.md screen 31).
     * Amounts use the same ₹ formatting as the prices screen.
     */
    fun priceChange(change: PriceChange, cityName: String) {
        val fuel = context.getString(PriceLabels.fuel(change.fuel))
        val unit = context.getString(PriceLabels.unit(change.fuel))
        val delta = IndianNumberFormat.rupees(Money(abs(change.delta.paise)))
        val title = context.getString(
            if (change.delta.isNegative) R.string.notif_price_down else R.string.notif_price_up,
            fuel,
            cityName,
            delta,
            unit,
        )
        val body = context.getString(R.string.notif_price_body, IndianNumberFormat.rupees(change.after), unit)
        val id = notificationId("price:${change.fuel}")
        post(id, base(CHANNEL_PRICE_ALERTS, title, body, open(NotificationRoutes.OPEN_PRICES, id)))
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
        val title = reminderTitle(r)
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

    /**
     * Replaces a reminder's notification after "Done" with "Marked done. Add what it cost?" (PRD F-11.4). Tapping it,
     * or "Add cost", opens the expense sheet pre-filled; the expense is linked to the completion when saved.
     */
    fun reminderDone(reminderId: String, title: String, cost: ReminderCost) {
        val id = notificationId(reminderId)
        val addCost = open(NotificationRoutes.OPEN_ADD_REMINDER_COST, id, cost.encode(), dismiss = true)
        val builder = base(
            CHANNEL_REMINDERS,
            context.getString(R.string.notif_done_title, title),
            context.getString(R.string.notif_done_body),
            addCost,
        )
            .setSilent(true)
            .addAction(0, context.getString(R.string.notif_action_add_cost), addCost)
        post(id, builder)
    }

    /** A reminder's title as its notification shows it. */
    fun reminderTitle(reminder: Reminder): String =
        reminder.title ?: context.getString(CareLabels.reminder(reminder.kind))

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

    /**
     * The Monday weekly digest (PRD §3 "Check"): last week's cost per km, distance and spend, and the change on the
     * week before. On the Insights channel with the monthly recap; the in-app "Weekly digest" switch turns it off.
     */
    fun weeklyDigest(vehicleName: String, costPerKm: String, km: String, spend: String, changePercent: Int?) {
        val change = changePercent?.let {
            when {
                it < 0 -> context.getString(R.string.notif_digest_cheaper, -it)
                it > 0 -> context.getString(R.string.notif_digest_dearer, it)
                else -> context.getString(R.string.notif_digest_same)
            }
        }
        post(
            DIGEST_ID,
            base(
                CHANNEL_INSIGHTS,
                context.getString(R.string.notif_digest_title, vehicleName, costPerKm),
                listOfNotNull(context.getString(R.string.notif_digest_body, km, spend), change).joinToString(" "),
                open(NotificationRoutes.OPEN_INSIGHTS, DIGEST_ID),
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

    private fun open(route: String, requestCode: Int, arg: String? = null, dismiss: Boolean = false): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(NotificationRoutes.EXTRA_OPEN, route)
            ?.apply { if (arg != null) putExtra(NotificationRoutes.EXTRA_ARG, arg) }
            ?.apply { if (dismiss) putExtra(NotificationRoutes.EXTRA_DISMISS, requestCode) }
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
        const val CHANNEL_REMINDERS = NotificationChannels.REMINDERS
        const val CHANNEL_INSIGHTS = NotificationChannels.INSIGHTS
        const val CHANNEL_PRICE_ALERTS = NotificationChannels.PRICE_ALERTS
        const val RECAP_ID = 1
        const val DIGEST_ID = 2
    }
}

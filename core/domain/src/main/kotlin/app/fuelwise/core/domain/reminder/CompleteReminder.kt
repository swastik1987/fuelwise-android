package app.fuelwise.core.domain.reminder

import app.fuelwise.core.model.Reminder
import java.time.ZoneId

/**
 * The reminder after a "Done" (PRD F-11.1): recurring ones move forward from the day and odometer of completion,
 * the way a service interval works; one-off ones return null and are retired.
 */
fun Reminder.nextAfterCompletion(completedAtUtcMs: Long, odometerKm: Double?, zone: ZoneId): Reminder? {
    val months = repeatMonths?.takeIf { it > 0 }
    val km = repeatKm?.takeIf { it > 0.0 }
    if (months == null && km == null) return null
    val nextDate = months?.let {
        completedAtUtcMs.localDate(zone).plusMonths(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val nextOdometer = km?.let { step -> (odometerKm ?: dueOdometerKm)?.plus(step) }
    return copy(dueDateUtcMs = nextDate, dueOdometerKm = nextOdometer, snoozedUntilUtcMs = null)
}

/** Snooze until local midnight [days] days from now (the notification's "Snooze 3 days"). */
fun Reminder.snoozed(nowUtcMs: Long, days: Long, zone: ZoneId): Reminder =
    copy(snoozedUntilUtcMs = nowUtcMs.localDate(zone).plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli())

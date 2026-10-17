package app.fuelwise.core.domain.reminder

import app.fuelwise.core.model.Reminder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.ceil

enum class Urgency { Overdue, Due, Soon, Later }

/** Where a reminder stands today. [daysLeft] and [kmLeft] are negative once passed. */
data class ReminderStatus(
    val reminder: Reminder,
    val daysLeft: Long?,
    val kmLeft: Double?,
    /** When the odometer is predicted to reach the due reading, from the recent km/day. */
    val predictedOdometerDate: LocalDate?,
    val urgency: Urgency,
    /** Post a notification today (PRD F-11.3); the daily check calls this once a day. */
    val notifyToday: Boolean,
)

/** What the evaluator needs to know about a vehicle's odometer. */
data class OdometerOutlook(val predictedKm: Double?, val kmPerDay: Double?, val windowKm: Double)

/**
 * Date and odometer reminder evaluation, whichever comes first (PRD F-11.1, F-11.3). Stateless: a date reminder
 * notifies on its "days before" offsets and the due day; an odometer reminder on the day the predicted reading
 * enters the due-soon window; anything overdue once a week until done or snoozed.
 */
class EvaluateReminders @Inject constructor(private val clock: Clock) {
    operator fun invoke(reminders: List<Reminder>, outlook: Map<String, OdometerOutlook>, zone: ZoneId): List<ReminderStatus> {
        val today = LocalDate.now(clock.withZone(zone))
        return reminders.map { r -> status(r, outlook[r.vehicleId], today, zone) }
            .sortedWith(compareBy({ it.urgency.ordinal }, { it.sortDays(today) }))
    }

    private fun status(r: Reminder, outlook: OdometerOutlook?, today: LocalDate, zone: ZoneId): ReminderStatus {
        val daysLeft = r.dueDateUtcMs?.let { ChronoUnit.DAYS.between(today, it.localDate(zone)) }
        val predicted = outlook?.predictedKm
        val kmLeft = r.dueOdometerKm?.let { due -> predicted?.let { due - it } }
        val perDay = outlook?.kmPerDay?.takeIf { it > 0.0 }
        val odoDays = kmLeft?.let { left -> perDay?.let { ceil(left / it).toLong() } }
        val window = outlook?.windowKm ?: 0.0
        val snoozed = r.snoozedUntilUtcMs?.let { it > clock.millis() } == true

        val overdue = (daysLeft != null && daysLeft < 0) || (kmLeft != null && kmLeft < 0)
        val due = (daysLeft != null && daysLeft <= DUE_DAYS) || (kmLeft != null && kmLeft <= window)
        val soon = (daysLeft != null && daysLeft <= SOON_DAYS) || (odoDays != null && odoDays <= SOON_DAYS)
        val urgency = when {
            overdue -> Urgency.Overdue
            due -> Urgency.Due
            soon -> Urgency.Soon
            else -> Urgency.Later
        }

        val dateHit = daysLeft != null && (daysLeft.toInt() in r.notifyDaysBefore || daysLeft == 0L)
        // Entered the window today: inside now, outside yesterday at the recent pace.
        val odoHit = kmLeft != null && kmLeft in 0.0..window && kmLeft + (perDay ?: 0.0) > window
        val weeklyNag = overdue && weeklyNagDay(r, daysLeft, today)
        return ReminderStatus(
            reminder = r,
            daysLeft = daysLeft,
            kmLeft = kmLeft,
            predictedOdometerDate = odoDays?.let { today.plusDays(it) },
            urgency = urgency,
            notifyToday = !snoozed && (dateHit || odoHit || weeklyNag),
        )
    }

    /** Every 7th day past a date; for odometer-only reminders a stable weekday per reminder. */
    private fun weeklyNagDay(r: Reminder, daysLeft: Long?, today: LocalDate): Boolean =
        if (daysLeft != null && daysLeft < 0) {
            -daysLeft % DAYS_PER_WEEK == 0L
        } else {
            Math.floorMod(r.id.hashCode(), DAYS_PER_WEEK) == today.dayOfWeek.value % DAYS_PER_WEEK
        }

    private fun ReminderStatus.sortDays(today: LocalDate): Long =
        listOfNotNull(daysLeft, predictedOdometerDate?.let { ChronoUnit.DAYS.between(today, it) }).minOrNull() ?: Long.MAX_VALUE

    private companion object {
        const val DUE_DAYS = 7L
        const val SOON_DAYS = 30L
        const val DAYS_PER_WEEK = 7
    }
}

internal fun Long.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

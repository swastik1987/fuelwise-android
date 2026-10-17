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
    operator fun invoke(
        reminders: List<Reminder>,
        outlook: Map<String, OdometerOutlook>,
        zone: ZoneId,
    ): List<ReminderStatus> {
        val today = LocalDate.now(clock.withZone(zone))
        return reminders.map { r -> status(r, outlook[r.vehicleId], today, zone) }
            .sortedWith(compareBy({ it.urgency.ordinal }, { it.sortDays(today) }))
    }

    private fun status(r: Reminder, outlook: OdometerOutlook?, today: LocalDate, zone: ZoneId): ReminderStatus {
        val daysLeft = r.dueDateUtcMs?.let { ChronoUnit.DAYS.between(today, it.localDate(zone)) }
        val kmLeft = r.dueOdometerKm?.let { due -> outlook?.predictedKm?.let { due - it } }
        val perDay = outlook?.kmPerDay?.takeIf { it > 0.0 }
        val odoDays = kmLeft?.let { left -> perDay?.let { ceil(left / it).toLong() } }
        val odo = OdoView(kmLeft, perDay, odoDays, outlook?.windowKm ?: 0.0)
        val urgency = urgency(daysLeft, odo)
        val snoozed = r.snoozedUntilUtcMs?.let { it > clock.millis() } == true
        val notify = dateHit(r, daysLeft) || odo.enteredWindowToday ||
            (urgency == Urgency.Overdue && weeklyNagDay(r, daysLeft, today))
        return ReminderStatus(
            reminder = r,
            daysLeft = daysLeft,
            kmLeft = kmLeft,
            predictedOdometerDate = odo.daysLeft?.let { today.plusDays(it) },
            urgency = urgency,
            notifyToday = !snoozed && notify,
        )
    }

    /** The odometer side of a reminder: km left, the recent pace and the due-soon window. */
    private data class OdoView(val kmLeft: Double?, val perDay: Double?, val daysLeft: Long?, val window: Double) {
        // Entered the window today: inside now, outside yesterday at the recent pace.
        val enteredWindowToday: Boolean
            get() = kmLeft != null && kmLeft in 0.0..window && kmLeft + (perDay ?: 0.0) > window
    }

    private fun urgency(daysLeft: Long?, odo: OdoView): Urgency {
        val km = odo.kmLeft
        val odoDays = odo.daysLeft
        return when {
            (daysLeft != null && daysLeft < 0) || (km != null && km < 0) -> Urgency.Overdue
            (daysLeft != null && daysLeft <= DUE_DAYS) || (km != null && km <= odo.window) -> Urgency.Due
            (daysLeft != null && daysLeft <= SOON_DAYS) || (odoDays != null && odoDays <= SOON_DAYS) -> Urgency.Soon
            else -> Urgency.Later
        }
    }

    private fun dateHit(r: Reminder, daysLeft: Long?): Boolean =
        daysLeft != null && (daysLeft.toInt() in r.notifyDaysBefore || daysLeft == 0L)

    /** Every 7th day past a date; for odometer-only reminders a stable weekday per reminder. */
    private fun weeklyNagDay(r: Reminder, daysLeft: Long?, today: LocalDate): Boolean =
        if (daysLeft != null && daysLeft < 0) {
            -daysLeft % DAYS_PER_WEEK == 0L
        } else {
            Math.floorMod(r.id.hashCode(), DAYS_PER_WEEK) == today.dayOfWeek.value % DAYS_PER_WEEK
        }

    private fun ReminderStatus.sortDays(today: LocalDate): Long =
        listOfNotNull(daysLeft, predictedOdometerDate?.let { ChronoUnit.DAYS.between(today, it) }).minOrNull()
            ?: Long.MAX_VALUE

    private companion object {
        const val DUE_DAYS = 7L
        const val SOON_DAYS = 30L
        const val DAYS_PER_WEEK = 7
    }
}

internal fun Long.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

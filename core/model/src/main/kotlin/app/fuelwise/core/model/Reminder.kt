package app.fuelwise.core.model

/** Reminder presets (PRD F-11.2). Default rules come from `assets/rules/india.json`, never from code. */
enum class ReminderKind {
    Insurance,
    Puc,
    Service,
    EngineOil,
    Tyres,
    Battery,
    Coolant,
    Wipers,
    Rc,
    Fitness,
    Licence,
    Warranty,
    FastagLowBalance,
    Custom,
}

/**
 * A reminder by date, by odometer, or both, whichever comes first (PRD F-11.1). Recurs every [repeatMonths]
 * and/or [repeatKm] after it is marked done.
 */
data class Reminder(
    val id: String,
    val vehicleId: String,
    val kind: ReminderKind,
    /** User title; null shows the preset's name. */
    val title: String?,
    val dueDateUtcMs: Long?,
    val dueOdometerKm: Double?,
    val repeatMonths: Int? = null,
    val repeatKm: Double? = null,
    /** Days before [dueDateUtcMs] to notify, e.g. 30, 15, 7, 1. */
    val notifyDaysBefore: List<Int> = DEFAULT_NOTIFY_DAYS,
    val snoozedUntilUtcMs: Long? = null,
    /** Set when created from a document's expiry (PRD F-11.5). */
    val documentId: String? = null,
    val notes: String? = null,
) {
    companion object {
        val DEFAULT_NOTIFY_DAYS = listOf(7, 1)
    }
}

/** A "Done" on a reminder; feeds the Done tab and moves recurring reminders forward. */
data class ReminderCompletion(
    val id: String,
    val reminderId: String,
    val vehicleId: String,
    val completedAtUtcMs: Long,
    val odometerKm: Double?,
    val expenseId: String? = null,
)

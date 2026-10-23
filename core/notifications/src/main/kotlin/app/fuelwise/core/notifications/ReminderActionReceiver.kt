package app.fuelwise.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fuelwise.core.data.repository.ReminderRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.reminder.ReminderCost
import app.fuelwise.core.domain.reminder.costCategory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Done" logs a completion (and moves recurring reminders on), then offers "Add cost"; "Snooze" waits 3 days
 * (PRD F-11.4).
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var notifier: Notifier

    @Inject
    @Dispatcher(FwDispatcher.IO)
    lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + io).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> done(id)

                    ACTION_SNOOZE -> {
                        reminders.snooze(id, SNOOZE_DAYS)
                        notifier.cancelReminder(id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Logs the completion, then offers "Add cost" where the reminder has one (PRD F-11.4). */
    private suspend fun done(id: String) {
        // Read first: completing a one-off reminder retires it.
        val reminder = reminders.reminder(id)
        val completionId = reminders.complete(id, odometerKm = null)
        val category = reminder?.kind?.costCategory()
        if (reminder == null || completionId == null || category == null) {
            notifier.cancelReminder(id)
        } else {
            notifier.reminderDone(
                id,
                notifier.reminderTitle(reminder),
                ReminderCost(completionId, reminder.vehicleId, category.id),
            )
        }
    }

    companion object {
        const val ACTION_DONE = "app.fuelwise.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.fuelwise.action.REMINDER_SNOOZE"
        const val EXTRA_REMINDER_ID = "reminderId"
        private const val SNOOZE_DAYS = 3L
    }
}

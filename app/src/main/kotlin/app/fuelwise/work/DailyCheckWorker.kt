package app.fuelwise.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FastagRepository
import app.fuelwise.core.data.repository.ObserveReminderStatuses
import app.fuelwise.core.data.repository.RecurrenceRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.recurrence.balance
import app.fuelwise.core.notifications.Notifier
import app.fuelwise.core.ui.format.IndianNumberFormat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * The once-a-day check, around the preferred time (PRD F-11.3; WorkManager, never exact alarms):
 * create due recurring expenses, post reminder notifications, warn on a low FASTag estimate, announce the monthly
 * recap on the 1st and send the weekly digest on Mondays (if on). Every step is idempotent, so a rerun after reboot,
 * update or restore is safe.
 */
@HiltWorker
class DailyCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recurrences: RecurrenceRepository,
    private val statuses: ObserveReminderStatuses,
    private val vehicles: VehicleRepository,
    private val fastag: FastagRepository,
    private val entries: EntryRepository,
    private val notifier: Notifier,
    private val insights: InsightNotifications,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        recurrences.createDueEntries()
        if (!inputData.getBoolean(KEY_NOTIFY, true)) return Result.success()

        val names = vehicles.observeVehicles().first().associate { it.id to it.name }
        statuses.now().filter { it.notifyToday }.forEach { status ->
            names[status.reminder.vehicleId]?.let { notifier.reminder(status, it) }
        }
        checkFastag(names)
        insights.monthlyRecap()
        insights.weeklyDigest()
        return Result.success()
    }

    /** Low balance: on the day a toll takes it under the threshold, then weekly on Mondays. */
    private suspend fun checkFastag(names: Map<String, String>) {
        val expenses = entries.observeAllExpenses().first()
        val recharges = fastag.observeRecharges().first()
        val today = LocalDate.now(clock.withZone(zoneId))
        val dayAgo = clock.millis() - DAY_MS
        fastag.observeAccounts().first().forEach { account ->
            val name = names[account.vehicleId] ?: return@forEach
            val balance = account.balance(recharges, expenses)
            val tollToday = expenses.any {
                it.vehicleId == account.vehicleId && it.tollPlaza != null &&
                    it.dateTimeUtcMs >= dayAgo
            }
            if (balance.isLow && (tollToday || today.dayOfWeek == DayOfWeek.MONDAY)) {
                notifier.fastagLow(name, IndianNumberFormat.rupeesWhole(balance.estimate))
            }
        }
    }

    companion object {
        const val KEY_NOTIFY = "notify"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

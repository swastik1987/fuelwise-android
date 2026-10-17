package app.fuelwise.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Keeps the daily check scheduled (PRD F-11.3). Called on every app start; KEEP makes it idempotent, and WorkManager
 * itself survives reboots and updates. A quick one-off run catches up recurring expenses right away.
 */
class WorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    fun schedule() {
        val work = WorkManager.getInstance(context)
        val daily = PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(PREFERRED_TIME).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniquePeriodicWork(DAILY_CHECK, ExistingPeriodicWorkPolicy.KEEP, daily)
        val catchUp = OneTimeWorkRequestBuilder<DailyCheckWorker>()
            .setInputData(workDataOf(DailyCheckWorker.KEY_NOTIFY to false))
            .build()
        work.enqueueUniqueWork(CATCH_UP, ExistingWorkPolicy.KEEP, catchUp)
    }

    /** Time until the next [time] in the user's zone (default 09:30, PRD F-11.3). */
    internal fun delayUntil(time: LocalTime): Duration {
        val now = ZonedDateTime.now(clock.withZone(zoneId))
        val today = now.with(time)
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        return Duration.between(now, next)
    }

    private companion object {
        const val DAILY_CHECK = "daily-check"
        const val CATCH_UP = "daily-check-catch-up"
        val PREFERRED_TIME: LocalTime = LocalTime.of(9, 30)
    }
}

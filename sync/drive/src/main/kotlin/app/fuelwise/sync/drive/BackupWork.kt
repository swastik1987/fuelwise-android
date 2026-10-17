package app.fuelwise.sync.drive

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.InvalidationTracker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.fuelwise.core.data.repository.BackupSettingsRepository
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.model.BackupError
import app.fuelwise.core.model.BackupFrequency
import app.fuelwise.core.model.BackupSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Runs one backup. Offline retries with backoff; other errors wait for the user's fix on the status screen. */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val outcome = backups.backupNow()) {
        is BackupOutcome.Success -> Result.success()

        is BackupOutcome.Failed -> if (outcome.error == BackupError.Offline &&
            runAttemptCount < MAX_RETRIES
        ) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 5
    }
}

/**
 * Keeps WorkManager in step with the backup settings (PRD F-14.3): a unique periodic job for daily or weekly, a
 * debounced one-time job 2 minutes after the last change for "after changes", nothing for manual. Wi-Fi only and
 * while-charging become constraints.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: BackupSettingsRepository,
    private val db: FuelwiseDatabase,
) {
    private val work get() = WorkManager.getInstance(context)
    private var current: BackupSettings? = null

    private val changes = object : InvalidationTracker.Observer(DATA_TABLES) {
        override fun onInvalidated(tables: Set<String>) {
            val s = current ?: return
            if (s.isSignedIn && s.frequency == BackupFrequency.AfterChanges) enqueueAfterChange(s)
        }
    }

    fun start(scope: CoroutineScope) {
        db.invalidationTracker.addObserver(changes)
        scope.launch {
            settings.settings
                .map { Schedule(it.frequency, it.wifiOnly, it.chargingOnly, it.isSignedIn) to it }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collect { (schedule, s) ->
                    current = s
                    apply(schedule)
                }
        }
        scope.launch { settings.settings.collect { current = it } }
    }

    /** "Back up now": runs once, as soon as the constraints allow. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    private data class Schedule(
        val frequency: BackupFrequency,
        val wifiOnly: Boolean,
        val charging: Boolean,
        val signedIn: Boolean,
    )

    private fun apply(s: Schedule) {
        val periodDays = when (s.frequency) {
            BackupFrequency.Daily -> 1L
            BackupFrequency.Weekly -> DAYS_PER_WEEK
            else -> null
        }
        if (!s.signedIn || periodDays == null) {
            work.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(periodDays, TimeUnit.DAYS)
            .setConstraints(constraints(s.wifiOnly, s.charging))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun enqueueAfterChange(s: BackupSettings) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints(s.wifiOnly, s.chargingOnly))
            .build()
        // REPLACE restarts the 2-minute wait on every change: one backup after a burst of edits.
        work.enqueueUniqueWork(AFTER_CHANGE, ExistingWorkPolicy.REPLACE, request)
    }

    private fun constraints(wifiOnly: Boolean, charging: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(charging)
        .build()

    private companion object {
        const val PERIODIC = "backup-periodic"
        const val AFTER_CHANGE = "backup-after-change"
        const val NOW = "backup-now"
        const val DEBOUNCE_MINUTES = 2L
        const val BACKOFF_MINUTES = 15L
        const val DAYS_PER_WEEK = 7L
        val DATA_TABLES = arrayOf(
            "vehicle", "tank", "category", "refuel", "expense", "reminder", "reminder_completion",
            "document", "attachment", "recurrence", "fastag_account", "fastag_recharge",
        )
    }
}

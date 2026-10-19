package app.fuelwise

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.fuelwise.core.notifications.Notifier
import app.fuelwise.di.ApplicationScope
import app.fuelwise.feature.prices.feed.PriceFeedScheduler
import app.fuelwise.sync.drive.BackupScheduler
import app.fuelwise.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@HiltAndroidApp
class FuelwiseApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notifier: Notifier

    @Inject lateinit var workScheduler: WorkScheduler

    @Inject lateinit var backupScheduler: BackupScheduler

    @Inject lateinit var priceFeedScheduler: PriceFeedScheduler

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    // WorkManager starts on demand with Hilt's factory; the default initializer is removed in the manifest.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        workScheduler.schedule()
        backupScheduler.start(appScope)
        priceFeedScheduler.start(appScope)
    }
}

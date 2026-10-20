package app.fuelwise.core.data.repository

import android.app.NotificationManager
import android.content.Context
import app.fuelwise.core.data.vault.AttachmentStore
import app.fuelwise.core.database.DatabaseSnapshotter
import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** "Delete all data" on this device (PRD F-18). Drive backups are separate: see the backup manager in sync:drive. */
fun interface LocalDataReset {
    /**
     * Deletes every row (tombstones too: this is a full reset, not a synced delete), every encrypted file and cached
     * share file, posted notifications, and resets preferences so onboarding runs again.
     */
    suspend fun deleteEverything()
}

internal class DeviceLocalDataReset @Inject constructor(
    private val snapshotter: DatabaseSnapshotter,
    private val attachments: AttachmentStore,
    private val preferences: PreferencesDataSource,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : LocalDataReset {
    override suspend fun deleteEverything() {
        withContext(io) {
            snapshotter.clearAll()
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        attachments.deleteAll()
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
        // Last: the preferences flip onboarding back on, which the app shell reacts to.
        preferences.reset()
    }
}

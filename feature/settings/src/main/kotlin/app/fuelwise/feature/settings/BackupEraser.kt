package app.fuelwise.feature.settings

import app.fuelwise.core.model.BackupError
import app.fuelwise.sync.drive.BackupManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The backup side of "Delete all data" (PRD F-18), behind an interface so the view model tests run without Drive. */
interface BackupEraser {
    /** Deletes every file in the Drive app folder. Null on success, or what stopped it. */
    suspend fun deleteDriveBackups(): BackupError?

    /** Forgets this device's backup key. */
    suspend fun forgetKey()
}

internal class DriveBackupEraser @Inject constructor(private val backups: BackupManager) : BackupEraser {
    override suspend fun deleteDriveBackups(): BackupError? = backups.deleteDriveBackups()

    override suspend fun forgetKey() = backups.forgetKey()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SettingsModule {
    @Binds
    fun backupEraser(impl: DriveBackupEraser): BackupEraser
}

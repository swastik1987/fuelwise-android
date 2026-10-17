package app.fuelwise.sync.drive

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
internal interface DriveModule {
    @Binds
    fun driveAuth(impl: GoogleDriveAuth): DriveAuth

    @Binds
    fun backupKeyStore(impl: TinkBackupKeyStore): BackupKeyStore

    companion object {
        @Provides
        @Named(DriveClient.DRIVE_BASE_URL)
        fun driveBaseUrl(): String = DriveClient.GOOGLE_APIS
    }
}

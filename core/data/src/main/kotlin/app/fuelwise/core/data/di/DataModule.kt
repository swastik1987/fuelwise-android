package app.fuelwise.core.data.di

import app.fuelwise.core.data.DataStoreDeviceIdProvider
import app.fuelwise.core.data.DeviceIdProvider
import app.fuelwise.core.data.repository.AssetCityRepository
import app.fuelwise.core.data.repository.AssetRulesRepository
import app.fuelwise.core.data.repository.BackupSettingsRepository
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.CityRepository
import app.fuelwise.core.data.repository.DataStoreBackupSettingsRepository
import app.fuelwise.core.data.repository.DataStorePriceSettingsRepository
import app.fuelwise.core.data.repository.DataStoreScanSettingsRepository
import app.fuelwise.core.data.repository.DataStoreUserDataRepository
import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.data.repository.EntitlementRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FastagRepository
import app.fuelwise.core.data.repository.FreeTierEntitlementRepository
import app.fuelwise.core.data.repository.FuelPriceRepository
import app.fuelwise.core.data.repository.ImportRepository
import app.fuelwise.core.data.repository.LogbookRepository
import app.fuelwise.core.data.repository.OfflineCategoryRepository
import app.fuelwise.core.data.repository.OfflineDocumentRepository
import app.fuelwise.core.data.repository.OfflineEntryRepository
import app.fuelwise.core.data.repository.OfflineFastagRepository
import app.fuelwise.core.data.repository.OfflineFuelPriceRepository
import app.fuelwise.core.data.repository.OfflineImportRepository
import app.fuelwise.core.data.repository.OfflineLogbookRepository
import app.fuelwise.core.data.repository.OfflineRecurrenceRepository
import app.fuelwise.core.data.repository.OfflineReminderRepository
import app.fuelwise.core.data.repository.OfflineTripRepository
import app.fuelwise.core.data.repository.OfflineVehicleRepository
import app.fuelwise.core.data.repository.PriceSettingsRepository
import app.fuelwise.core.data.repository.RecurrenceRepository
import app.fuelwise.core.data.repository.ReminderRepository
import app.fuelwise.core.data.repository.RulesRepository
import app.fuelwise.core.data.repository.ScanSettingsRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.data.vault.AttachmentStore
import app.fuelwise.core.data.vault.TinkAttachmentStore
import app.fuelwise.core.data.vault.TinkVaultCipher
import app.fuelwise.core.data.vault.VaultCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId

// One binding per repository; the count grows with features, not with complexity.
@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
internal interface DataModule {
    @Binds
    fun vehicleRepository(impl: OfflineVehicleRepository): VehicleRepository

    @Binds
    fun categoryRepository(impl: OfflineCategoryRepository): CategoryRepository

    @Binds
    fun entryRepository(impl: OfflineEntryRepository): EntryRepository

    @Binds
    fun logbookRepository(impl: OfflineLogbookRepository): LogbookRepository

    @Binds
    fun userDataRepository(impl: DataStoreUserDataRepository): UserDataRepository

    @Binds
    fun scanSettingsRepository(impl: DataStoreScanSettingsRepository): ScanSettingsRepository

    @Binds
    fun entitlementRepository(impl: FreeTierEntitlementRepository): EntitlementRepository

    @Binds
    fun reminderRepository(impl: OfflineReminderRepository): ReminderRepository

    @Binds
    fun documentRepository(impl: OfflineDocumentRepository): DocumentRepository

    @Binds
    fun recurrenceRepository(impl: OfflineRecurrenceRepository): RecurrenceRepository

    @Binds
    fun fastagRepository(impl: OfflineFastagRepository): FastagRepository

    @Binds
    fun rulesRepository(impl: AssetRulesRepository): RulesRepository

    @Binds
    fun attachmentStore(impl: TinkAttachmentStore): AttachmentStore

    @Binds
    fun vaultCipher(impl: TinkVaultCipher): VaultCipher

    @Binds
    fun backupSettingsRepository(impl: DataStoreBackupSettingsRepository): BackupSettingsRepository

    @Binds
    fun importRepository(impl: OfflineImportRepository): ImportRepository

    @Binds
    fun tripRepository(impl: OfflineTripRepository): TripRepository

    @Binds
    fun fuelPriceRepository(impl: OfflineFuelPriceRepository): FuelPriceRepository

    @Binds
    fun priceSettingsRepository(impl: DataStorePriceSettingsRepository): PriceSettingsRepository

    @Binds
    fun cityRepository(impl: AssetCityRepository): CityRepository

    @Binds
    fun deviceIdProvider(impl: DataStoreDeviceIdProvider): DeviceIdProvider

    companion object {
        /** The user's time zone, stored next to UTC timestamps (CLAUDE.md: UTC ms plus zoneId). */
        @Provides
        fun zoneId(): ZoneId = ZoneId.systemDefault()
    }
}

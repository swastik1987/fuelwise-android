package app.fuelwise.core.data.di

import app.fuelwise.core.data.DataStoreDeviceIdProvider
import app.fuelwise.core.data.DeviceIdProvider
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.DataStoreUserDataRepository
import app.fuelwise.core.data.repository.EntitlementRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FreeTierEntitlementRepository
import app.fuelwise.core.data.repository.LogbookRepository
import app.fuelwise.core.data.repository.OfflineCategoryRepository
import app.fuelwise.core.data.repository.OfflineEntryRepository
import app.fuelwise.core.data.repository.OfflineLogbookRepository
import app.fuelwise.core.data.repository.OfflineVehicleRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
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
    fun entitlementRepository(impl: FreeTierEntitlementRepository): EntitlementRepository

    @Binds
    fun deviceIdProvider(impl: DataStoreDeviceIdProvider): DeviceIdProvider

    companion object {
        /** The user's time zone, stored next to UTC timestamps (CLAUDE.md: UTC ms plus zoneId). */
        @Provides
        fun zoneId(): ZoneId = ZoneId.systemDefault()
    }
}

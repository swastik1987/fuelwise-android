package app.fuelwise.core.database.di

import android.content.Context
import androidx.room.Room
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.database.dao.CategoryDao
import app.fuelwise.core.database.dao.EntryDao
import app.fuelwise.core.database.dao.VehicleDao
import app.fuelwise.core.database.migration.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FuelwiseDatabase =
        Room.databaseBuilder(context, FuelwiseDatabase::class.java, FuelwiseDatabase.NAME)
            .addCallback(FuelwiseDatabase.SEED_CALLBACK)
            // One explicit migration per version step. No destructive fallback, ever.
            .apply { Migrations.ALL.forEach { addMigrations(it) } }
            .build()

    @Provides
    fun vehicleDao(db: FuelwiseDatabase): VehicleDao = db.vehicleDao()

    @Provides
    fun categoryDao(db: FuelwiseDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun entryDao(db: FuelwiseDatabase): EntryDao = db.entryDao()
}

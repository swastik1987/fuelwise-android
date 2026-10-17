package app.fuelwise.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.fuelwise.core.database.dao.CategoryDao
import app.fuelwise.core.database.dao.EntryDao
import app.fuelwise.core.database.dao.VehicleDao
import app.fuelwise.core.database.model.CategoryEntity
import app.fuelwise.core.database.model.ExpenseEntity
import app.fuelwise.core.database.model.RefuelEntity
import app.fuelwise.core.database.model.TankEntity
import app.fuelwise.core.database.model.VehicleEntity

/**
 * The app database. Every version bump needs an explicit Migration plus a migration test, and the exported
 * schema JSON committed under core/database/schemas (CLAUDE.md rule 9). Never use destructive fallback.
 */
@Database(
    entities = [
        VehicleEntity::class,
        TankEntity::class,
        CategoryEntity::class,
        RefuelEntity::class,
        ExpenseEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class FuelwiseDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao

    abstract fun categoryDao(): CategoryDao

    abstract fun entryDao(): EntryDao

    companion object {
        const val NAME = "fuelwise.db"

        /** Inserts the system expense categories when the database is first created. */
        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                SystemCategorySeed.rows.forEach { row ->
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO category
                            (id, systemKey, customName, icon, color, isHidden, includeInCostPerKm, sortOrder,
                             createdAt, updatedAt, deviceId, deletedAt)
                        VALUES (?, ?, NULL, NULL, NULL, 0, ?, ?, 0, 0, ?, NULL)
                        """.trimIndent(),
                        arrayOf<Any>(
                            row.id,
                            row.systemKey,
                            if (row.includeInCostPerKm) 1 else 0,
                            row.sortOrder,
                            SEED_DEVICE,
                        ),
                    )
                }
            }
        }

        /** Device id recorded on seeded rows, so they never win a sync merge against a user's edit. */
        const val SEED_DEVICE = "seed"
    }
}

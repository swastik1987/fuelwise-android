package app.fuelwise.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations, one per version step (CLAUDE.md rule 9). SQL is copied from the exported schema JSON so
 * the migrated database matches a freshly created one exactly; MigrationTest checks that.
 */
object Migrations {
    /** v2 (M3): fill-ups/charges and expenses. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `refuel` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                    "`tankId` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, " +
                    "`odometerKm` REAL NOT NULL, `quantity` REAL NOT NULL, `unitPricePaise` INTEGER NOT NULL, " +
                    "`totalPaise` INTEGER NOT NULL, `discountPaise` INTEGER NOT NULL, `paidPaise` INTEGER NOT NULL, " +
                    "`isFull` INTEGER NOT NULL, `missedPrevious` INTEGER NOT NULL, `grade` TEXT, `stationName` TEXT, " +
                    "`stationBrand` TEXT, `lat` REAL, `lng` REAL, `paymentMethod` TEXT, `priceSource` TEXT NOT NULL, " +
                    "`notes` TEXT, `socStart` REAL, `socEnd` REAL, `chargerType` TEXT, `chargeDurationMin` INTEGER, " +
                    "`operator` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`deviceId` TEXT NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_refuel_vehicleId_dateTime` ON `refuel` (`vehicleId`, `dateTime`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_refuel_tankId` ON `refuel` (`tankId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `expense` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                    "`categoryId` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, " +
                    "`odometerKm` REAL, `amountPaise` INTEGER NOT NULL, `paymentMethod` TEXT, `notes` TEXT, " +
                    "`tripId` TEXT, `tollPlaza` TEXT, `recurrenceId` TEXT, `isAutoCreated` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, " +
                    "`deletedAt` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_expense_vehicleId_dateTime` ON `expense` (`vehicleId`, `dateTime`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categoryId` ON `expense` (`categoryId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

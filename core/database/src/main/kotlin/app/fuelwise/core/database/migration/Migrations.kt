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

    /**
     * v3 (M6): reminders and completions, documents and attachments, recurring expenses, FASTag accounts and
     * recharges; a unique index so a recurring expense is created once per due date.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V3_STATEMENTS.forEach { db.execSQL(it) }
        }
    }

    /** v4 (F-13.2): import batches, and the batch id on fill-ups and expenses so an import can be undone. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V4_STATEMENTS.forEach { db.execSQL(it) }
        }
    }

    /** v5 (F-07.1): trips. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V5_STATEMENTS.forEach { db.execSQL(it) }
        }
    }

    /** v6 (F-07.2): GPS trip stats and encrypted raw point batches. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V6_STATEMENTS.forEach { db.execSQL(it) }
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}

private const val SYNC_COLUMNS =
    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, `deletedAt` INTEGER"

private val V6_STATEMENTS = listOf(
    "ALTER TABLE `trip` ADD COLUMN `recordedSeconds` INTEGER",
    "ALTER TABLE `trip` ADD COLUMN `idleSeconds` INTEGER",
    "ALTER TABLE `trip` ADD COLUMN `maxSpeedKmh` REAL",
    "CREATE TABLE IF NOT EXISTS `trip_point_batch` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, " +
        "`seq` INTEGER NOT NULL, `payload` BLOB NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_trip_point_batch_tripId_seq` ON `trip_point_batch` (`tripId`, `seq`)",
)

private val V5_STATEMENTS = listOf(
    "CREATE TABLE IF NOT EXISTS `trip` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `startTime` INTEGER NOT NULL, " +
        "`endTime` INTEGER, `zoneId` TEXT NOT NULL, `startOdo` REAL, `endOdo` REAL, `distanceKm` REAL NOT NULL, " +
        "`purpose` TEXT NOT NULL, `customPurpose` TEXT, `fromPlace` TEXT, `toPlace` TEXT, `notes` TEXT, " +
        "`polylineAttachmentId` TEXT, `estCostPaise` INTEGER, `source` TEXT NOT NULL, `status` TEXT NOT NULL, " +
        "$SYNC_COLUMNS, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_trip_vehicleId_startTime` ON `trip` (`vehicleId`, `startTime`)",
    // Tolls and parking on a trip are looked up by trip.
    "CREATE INDEX IF NOT EXISTS `index_expense_tripId` ON `expense` (`tripId`)",
)

private val V4_STATEMENTS = listOf(
    "ALTER TABLE `refuel` ADD COLUMN `importBatchId` TEXT",
    "ALTER TABLE `expense` ADD COLUMN `importBatchId` TEXT",
    "CREATE INDEX IF NOT EXISTS `index_refuel_importBatchId` ON `refuel` (`importBatchId`)",
    "CREATE INDEX IF NOT EXISTS `index_expense_importBatchId` ON `expense` (`importBatchId`)",
    "CREATE TABLE IF NOT EXISTS `import_batch` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `fileName` TEXT, " +
        "`importedAt` INTEGER NOT NULL, `refuelCount` INTEGER NOT NULL, `expenseCount` INTEGER NOT NULL, " +
        "$SYNC_COLUMNS, PRIMARY KEY(`id`))",
)

private val V3_STATEMENTS = listOf(
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_recurrenceId_dateTime` " +
        "ON `expense` (`recurrenceId`, `dateTime`)",
    "CREATE TABLE IF NOT EXISTS `reminder` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
        "`title` TEXT, `dueDate` INTEGER, `dueOdometerKm` REAL, `repeatMonths` INTEGER, `repeatKm` REAL, " +
        "`notifyDaysBefore` TEXT NOT NULL, `snoozedUntil` INTEGER, `documentId` TEXT, `notes` TEXT, " +
        "$SYNC_COLUMNS, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_reminder_vehicleId` ON `reminder` (`vehicleId`)",
    "CREATE INDEX IF NOT EXISTS `index_reminder_documentId` ON `reminder` (`documentId`)",
    "CREATE TABLE IF NOT EXISTS `reminder_completion` (`id` TEXT NOT NULL, `reminderId` TEXT NOT NULL, " +
        "`vehicleId` TEXT NOT NULL, `completedAt` INTEGER NOT NULL, `odometerKm` REAL, `expenseId` TEXT, " +
        "$SYNC_COLUMNS, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_reminder_completion_reminderId` ON `reminder_completion` (`reminderId`)",
    "CREATE INDEX IF NOT EXISTS `index_reminder_completion_vehicleId` ON `reminder_completion` (`vehicleId`)",
    "CREATE TABLE IF NOT EXISTS `document` (`id` TEXT NOT NULL, `vehicleId` TEXT, `type` TEXT NOT NULL, " +
        "`number` TEXT, `issuer` TEXT, `issueDate` INTEGER, `expiryDate` INTEGER, `notes` TEXT, `insurer` TEXT, " +
        "`policyType` TEXT, `idvPaise` INTEGER, `ncbPercent` INTEGER, `addOns` TEXT, " +
        "`remindOnExpiry` INTEGER NOT NULL, " +
        "$SYNC_COLUMNS, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_document_vehicleId` ON `document` (`vehicleId`)",
    "CREATE TABLE IF NOT EXISTS `attachment` (`id` TEXT NOT NULL, `ownerId` TEXT NOT NULL, " +
        "`ownerType` TEXT NOT NULL, `storedName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, " +
        "`sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, " +
        "$SYNC_COLUMNS, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_attachment_ownerId` ON `attachment` (`ownerId`)",
    "CREATE TABLE IF NOT EXISTS `recurrence` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
        "`categoryId` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `every` INTEGER NOT NULL, " +
        "`unit` TEXT NOT NULL, `startDate` INTEGER NOT NULL, " +
        "`zoneId` TEXT NOT NULL, `endDate` INTEGER, `paymentMethod` TEXT, `notes` TEXT, " +
        "$SYNC_COLUMNS, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_recurrence_vehicleId` ON `recurrence` (`vehicleId`)",
    "CREATE TABLE IF NOT EXISTS `fastag_account` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `issuer` TEXT, " +
        "`tagLast4` TEXT, `anchorBalancePaise` INTEGER NOT NULL, `anchorAt` INTEGER NOT NULL, " +
        "`lowBalancePaise` INTEGER NOT NULL, $SYNC_COLUMNS, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_fastag_account_vehicleId` ON `fastag_account` (`vehicleId`)",
    "CREATE TABLE IF NOT EXISTS `fastag_recharge` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
        "`vehicleId` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, " +
        "`amountPaise` INTEGER NOT NULL, " +
        "`notes` TEXT, $SYNC_COLUMNS, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_fastag_recharge_accountId` ON `fastag_recharge` (`accountId`)",
    "CREATE INDEX IF NOT EXISTS `index_fastag_recharge_vehicleId_dateTime` " +
        "ON `fastag_recharge` (`vehicleId`, `dateTime`)",
)

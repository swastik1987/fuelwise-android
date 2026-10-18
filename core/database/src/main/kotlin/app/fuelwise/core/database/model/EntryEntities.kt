package app.fuelwise.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** PRD §7 `refuel`: fill-ups and EV charge sessions. Money in paise; quantity in the tank's canonical unit. */
@Entity(
    tableName = "refuel",
    indices = [Index("vehicleId", "dateTime"), Index("tankId"), Index("importBatchId")],
)
data class RefuelEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val tankId: String,
    val dateTime: Long,
    val zoneId: String,
    val odometerKm: Double,
    val quantity: Double,
    val unitPricePaise: Long,
    val totalPaise: Long,
    val discountPaise: Long,
    val paidPaise: Long,
    val isFull: Boolean,
    val missedPrevious: Boolean,
    val grade: String?,
    val stationName: String?,
    val stationBrand: String?,
    val lat: Double?,
    val lng: Double?,
    val paymentMethod: String?,
    val priceSource: String,
    val notes: String?,
    val socStart: Double?,
    val socEnd: Double?,
    val chargerType: String?,
    val chargeDurationMin: Int?,
    val operator: String?,
    /** Set on rows a file import created, so the whole import can be undone (PRD F-13.2). */
    val importBatchId: String? = null,
    @Embedded val sync: SyncMeta,
)

/** PRD §7 `expense`. Tolls set [tollPlaza]. Recurring expenses (M6) fill [recurrenceId] and [isAutoCreated]. */
@Entity(
    tableName = "expense",
    indices = [
        Index("vehicleId", "dateTime"),
        Index("categoryId"),
        // A recurring expense is created at most once per due date, even after a restore (PRD F-04.3 AC).
        Index(value = ["recurrenceId", "dateTime"], unique = true),
        Index("importBatchId"),
    ],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val categoryId: String,
    val dateTime: Long,
    val zoneId: String,
    val odometerKm: Double?,
    val amountPaise: Long,
    val paymentMethod: String?,
    val notes: String?,
    val tripId: String?,
    val tollPlaza: String?,
    val recurrenceId: String?,
    val isAutoCreated: Boolean,
    val importBatchId: String? = null,
    @Embedded val sync: SyncMeta,
)

/**
 * One file import (PRD F-13.2): where it came from and how many rows it added. Undo tombstones the batch and every
 * row carrying its id.
 */
@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val source: String,
    val fileName: String?,
    val importedAt: Long,
    val refuelCount: Int,
    val expenseCount: Int,
    @Embedded val sync: SyncMeta,
)

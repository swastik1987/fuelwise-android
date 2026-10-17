package app.fuelwise.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** PRD §7 `reminder` (F-11). [notifyDaysBefore] is a comma-separated list, e.g. "30,15,7,1". */
@Entity(tableName = "reminder", indices = [Index("vehicleId"), Index("documentId")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val kind: String,
    val title: String?,
    val dueDate: Long?,
    val dueOdometerKm: Double?,
    val repeatMonths: Int?,
    val repeatKm: Double?,
    val notifyDaysBefore: String,
    val snoozedUntil: Long?,
    val documentId: String?,
    val notes: String?,
    @Embedded val sync: SyncMeta,
)

/** A "Done" on a reminder. */
@Entity(tableName = "reminder_completion", indices = [Index("reminderId"), Index("vehicleId")])
data class ReminderCompletionEntity(
    @PrimaryKey val id: String,
    val reminderId: String,
    val vehicleId: String,
    val completedAt: Long,
    val odometerKm: Double?,
    val expenseId: String?,
    @Embedded val sync: SyncMeta,
)

/** PRD §7 `document` (F-12). Insurance extras are flattened; IDV is paise. */
@Entity(tableName = "document", indices = [Index("vehicleId")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val vehicleId: String?,
    val type: String,
    val number: String?,
    val issuer: String?,
    val issueDate: Long?,
    val expiryDate: Long?,
    val notes: String?,
    val insurer: String?,
    val policyType: String?,
    val idvPaise: Long?,
    val ncbPercent: Int?,
    val addOns: String?,
    val remindOnExpiry: Boolean,
    @Embedded val sync: SyncMeta,
)

/** An encrypted file in app storage, owned by a document or an expense. */
@Entity(tableName = "attachment", indices = [Index("ownerId")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val ownerType: String,
    val storedName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    @Embedded val sync: SyncMeta,
)

/** PRD §7 `recurrence` (F-04.3). */
@Entity(tableName = "recurrence", indices = [Index("vehicleId")])
data class RecurrenceEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val categoryId: String,
    val amountPaise: Long,
    val every: Int,
    val unit: String,
    val startDate: Long,
    val zoneId: String,
    val endDate: Long?,
    val paymentMethod: String?,
    val notes: String?,
    @Embedded val sync: SyncMeta,
)

/** PRD §7 `fastag_account` (F-10.1). */
@Entity(tableName = "fastag_account", indices = [Index("vehicleId")])
data class FastagAccountEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val issuer: String?,
    val tagLast4: String?,
    val anchorBalancePaise: Long,
    val anchorAt: Long,
    val lowBalancePaise: Long,
    @Embedded val sync: SyncMeta,
)

/** PRD §7 `fastag_recharge` (F-10.3). */
@Entity(tableName = "fastag_recharge", indices = [Index("accountId"), Index("vehicleId", "dateTime")])
data class FastagRechargeEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val vehicleId: String,
    val dateTime: Long,
    val zoneId: String,
    val amountPaise: Long,
    val notes: String?,
    @Embedded val sync: SyncMeta,
)

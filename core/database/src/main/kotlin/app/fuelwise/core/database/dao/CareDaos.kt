package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.fuelwise.core.database.model.AttachmentEntity
import app.fuelwise.core.database.model.DocumentEntity
import app.fuelwise.core.database.model.ExpenseEntity
import app.fuelwise.core.database.model.FastagAccountEntity
import app.fuelwise.core.database.model.FastagRechargeEntity
import app.fuelwise.core.database.model.RecurrenceEntity
import app.fuelwise.core.database.model.ReminderCompletionEntity
import app.fuelwise.core.database.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

/** Reminders and their completions (PRD F-11). Reads skip tombstones. */
@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder WHERE deletedAt IS NULL")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE deletedAt IS NULL")
    suspend fun reminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id AND deletedAt IS NULL")
    suspend fun reminder(id: String): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE documentId = :documentId AND deletedAt IS NULL LIMIT 1")
    suspend fun reminderForDocument(documentId: String): ReminderEntity?

    @Upsert
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Query(
        """
        UPDATE reminder SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneReminder(id: String, at: Long, deviceId: String)

    @Query("SELECT * FROM reminder_completion WHERE deletedAt IS NULL ORDER BY completedAt DESC")
    fun observeCompletions(): Flow<List<ReminderCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: ReminderCompletionEntity)

    /** Logs the completion and moves (or retires) the reminder in one transaction. */
    @Transaction
    suspend fun complete(
        completion: ReminderCompletionEntity,
        next: ReminderEntity?,
        retireId: String?,
        at: Long,
        deviceId: String,
    ) {
        insertCompletion(completion)
        if (next != null) upsertReminder(next)
        if (retireId != null) tombstoneReminder(retireId, at, deviceId)
    }
}

/** Documents and attachments (PRD F-12). */
@Dao
interface DocumentDao {
    @Query("SELECT * FROM document WHERE deletedAt IS NULL ORDER BY expiryDate IS NULL, expiryDate")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document WHERE id = :id AND deletedAt IS NULL")
    fun observeDocument(id: String): Flow<DocumentEntity?>

    @Upsert
    suspend fun upsertDocument(document: DocumentEntity)

    @Query(
        """
        UPDATE document SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneDocument(id: String, at: Long, deviceId: String)

    @Query("SELECT * FROM attachment WHERE ownerId = :ownerId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeAttachments(ownerId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachment WHERE id = :id AND deletedAt IS NULL")
    suspend fun attachment(id: String): AttachmentEntity?

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Query(
        """
        UPDATE attachment SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneAttachment(id: String, at: Long, deviceId: String)
}

/** Recurring expenses (PRD F-04.3). */
@Dao
interface RecurrenceDao {
    @Query("SELECT * FROM recurrence WHERE deletedAt IS NULL")
    fun observeRecurrences(): Flow<List<RecurrenceEntity>>

    @Query("SELECT * FROM recurrence WHERE deletedAt IS NULL")
    suspend fun recurrences(): List<RecurrenceEntity>

    @Upsert
    suspend fun upsertRecurrence(recurrence: RecurrenceEntity)

    @Query(
        """
        UPDATE recurrence SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneRecurrence(id: String, at: Long, deviceId: String)

    /** Due dates already created for [recurrenceId], tombstoned ones included so a deleted entry stays deleted. */
    @Query("SELECT dateTime FROM expense WHERE recurrenceId = :recurrenceId")
    suspend fun createdDueDates(recurrenceId: String): List<Long>

    /** Ignored when `recurrenceId + dateTime` already exists (unique index): never double-creates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAutoExpense(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expense WHERE isAutoCreated = 1 AND deletedAt IS NULL ORDER BY dateTime DESC")
    fun observeUnconfirmed(): Flow<List<ExpenseEntity>>

    @Query("UPDATE expense SET isAutoCreated = 0, updatedAt = :at, deviceId = :deviceId WHERE id = :id")
    suspend fun confirm(id: String, at: Long, deviceId: String)
}

/** FASTag accounts and recharges (PRD F-10.1, F-10.3). */
@Dao
interface FastagDao {
    @Query("SELECT * FROM fastag_account WHERE deletedAt IS NULL")
    fun observeAccounts(): Flow<List<FastagAccountEntity>>

    @Upsert
    suspend fun upsertAccount(account: FastagAccountEntity)

    @Query("SELECT * FROM fastag_recharge WHERE deletedAt IS NULL ORDER BY dateTime DESC")
    fun observeRecharges(): Flow<List<FastagRechargeEntity>>

    @Upsert
    suspend fun upsertRecharge(recharge: FastagRechargeEntity)

    @Query(
        """
        UPDATE fastag_recharge SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneRecharge(id: String, at: Long, deviceId: String)
}

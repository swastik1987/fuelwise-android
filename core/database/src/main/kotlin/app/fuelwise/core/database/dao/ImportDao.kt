package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.fuelwise.core.database.model.ExpenseEntity
import app.fuelwise.core.database.model.ImportBatchEntity
import app.fuelwise.core.database.model.RefuelEntity
import kotlinx.coroutines.flow.Flow

/** File imports (PRD F-13.2). A batch lands in one transaction, and undo tombstones it with all its rows. */
@Dao
interface ImportDao {
    @Insert
    suspend fun insertBatch(batch: ImportBatchEntity)

    @Insert
    suspend fun insertRefuels(refuels: List<RefuelEntity>)

    @Insert
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Transaction
    suspend fun commit(batch: ImportBatchEntity, refuels: List<RefuelEntity>, expenses: List<ExpenseEntity>) {
        insertBatch(batch)
        insertRefuels(refuels)
        insertExpenses(expenses)
    }

    @Query("SELECT * FROM import_batch WHERE deletedAt IS NULL ORDER BY importedAt DESC LIMIT :limit")
    fun observeBatches(limit: Int): Flow<List<ImportBatchEntity>>

    @Query(
        """
        UPDATE refuel SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE importBatchId = :batchId AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneRefuels(batchId: String, at: Long, deviceId: String)

    @Query(
        """
        UPDATE expense SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE importBatchId = :batchId AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneExpenses(batchId: String, at: Long, deviceId: String)

    @Query(
        "UPDATE import_batch SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId " +
            "WHERE id = :batchId AND deletedAt IS NULL",
    )
    suspend fun tombstoneBatch(batchId: String, at: Long, deviceId: String)

    @Transaction
    suspend fun undo(batchId: String, at: Long, deviceId: String) {
        tombstoneRefuels(batchId, at, deviceId)
        tombstoneExpenses(batchId, at, deviceId)
        tombstoneBatch(batchId, at, deviceId)
    }
}

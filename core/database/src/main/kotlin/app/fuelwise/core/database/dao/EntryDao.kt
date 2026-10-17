package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.fuelwise.core.database.model.ExpenseEntity
import app.fuelwise.core.database.model.RefuelEntity
import kotlinx.coroutines.flow.Flow

/** Fill-ups, charges and expenses. Reads skip tombstones; deletes are tombstones with a timestamp for undo. */
@Dao
interface EntryDao {
    @Query("SELECT * FROM refuel WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY dateTime")
    fun observeRefuels(vehicleId: String): Flow<List<RefuelEntity>>

    @Query("SELECT * FROM refuel WHERE deletedAt IS NULL ORDER BY dateTime")
    fun observeAllRefuels(): Flow<List<RefuelEntity>>

    @Query("SELECT * FROM refuel WHERE id = :id AND deletedAt IS NULL")
    fun observeRefuel(id: String): Flow<RefuelEntity?>

    @Upsert
    suspend fun upsertRefuel(refuel: RefuelEntity)

    @Query("SELECT * FROM expense WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY dateTime")
    fun observeExpenses(vehicleId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE deletedAt IS NULL ORDER BY dateTime")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE id = :id AND deletedAt IS NULL")
    fun observeExpense(id: String): Flow<ExpenseEntity?>

    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntity)

    /** Plaza names used before, most recent first, for the toll sheet's suggestions (design.md screen 11). */
    @Query(
        """
        SELECT tollPlaza FROM expense
        WHERE tollPlaza IS NOT NULL AND deletedAt IS NULL
        GROUP BY tollPlaza ORDER BY MAX(dateTime) DESC LIMIT :limit
        """,
    )
    fun observeRecentPlazas(limit: Int): Flow<List<String>>

    @Query(
        "UPDATE refuel SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId WHERE id = :id AND deletedAt IS NULL",
    )
    suspend fun tombstoneRefuel(id: String, at: Long, deviceId: String)

    @Query(
        """
        UPDATE refuel SET deletedAt = NULL, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt = :deletedAt
        """,
    )
    suspend fun restoreRefuel(id: String, deletedAt: Long, at: Long, deviceId: String)

    @Query(
        """
        UPDATE expense SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneExpense(id: String, at: Long, deviceId: String)

    @Query(
        """
        UPDATE expense SET deletedAt = NULL, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt = :deletedAt
        """,
    )
    suspend fun restoreExpense(id: String, deletedAt: Long, at: Long, deviceId: String)
}

package app.fuelwise.core.data.repository

import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import kotlinx.coroutines.flow.Flow

/** Fill-ups, charges and expenses. Room is the source of truth; deletes are tombstones with undo (PRD F-02 AC). */
interface EntryRepository {
    fun observeRefuels(vehicleId: String): Flow<List<Refuel>>

    fun observeAllRefuels(): Flow<List<Refuel>>

    fun observeRefuel(id: String): Flow<Refuel?>

    fun observeExpenses(vehicleId: String): Flow<List<Expense>>

    fun observeAllExpenses(): Flow<List<Expense>>

    fun observeExpense(id: String): Flow<Expense?>

    fun observeRecentPlazas(): Flow<List<String>>

    /** Inserts or replaces. A blank [Refuel.id] means "new": an id is assigned and returned. */
    suspend fun saveRefuel(refuel: Refuel): String

    suspend fun saveExpense(expense: Expense): String

    suspend fun deleteRefuel(id: String): DeletedEntry

    suspend fun deleteExpense(id: String): DeletedEntry

    suspend fun undoDelete(token: DeletedEntry)
}

/** Undo token for entry deletes. */
data class DeletedEntry(val kind: Kind, val id: String, val deletedAtUtcMs: Long) {
    enum class Kind { Refuel, Expense }
}

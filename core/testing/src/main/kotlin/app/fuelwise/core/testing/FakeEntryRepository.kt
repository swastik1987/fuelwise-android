package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.DeletedEntry
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [EntryRepository] for ViewModel and UI tests. */
class FakeEntryRepository(refuels: List<Refuel> = emptyList(), expenses: List<Expense> = emptyList()) :
    EntryRepository {
    private val refuelState = MutableStateFlow(refuels)
    private val expenseState = MutableStateFlow(expenses)
    private val deletedRefuels = mutableMapOf<String, Refuel>()
    private val deletedExpenses = mutableMapOf<String, Expense>()
    private var nextId = 1

    val refuels: List<Refuel> get() = refuelState.value
    val expenses: List<Expense> get() = expenseState.value

    override fun observeRefuels(vehicleId: String): Flow<List<Refuel>> =
        refuelState.map { all -> all.filter { it.vehicleId == vehicleId }.sortedBy { it.dateTimeUtcMs } }

    override fun observeAllRefuels(): Flow<List<Refuel>> = refuelState.map { it.sortedBy { r -> r.dateTimeUtcMs } }

    override fun observeRefuel(id: String): Flow<Refuel?> = refuelState.map { all -> all.firstOrNull { it.id == id } }

    override fun observeExpenses(vehicleId: String): Flow<List<Expense>> =
        expenseState.map { all -> all.filter { it.vehicleId == vehicleId }.sortedBy { it.dateTimeUtcMs } }

    override fun observeAllExpenses(): Flow<List<Expense>> = expenseState.map { it.sortedBy { e -> e.dateTimeUtcMs } }

    override fun observeExpense(id: String): Flow<Expense?> = expenseState.map { all ->
        all.firstOrNull { it.id == id }
    }

    override fun observeRecentPlazas(): Flow<List<String>> =
        expenseState.map { all -> all.sortedByDescending { it.dateTimeUtcMs }.mapNotNull { it.tollPlaza }.distinct() }

    override suspend fun saveRefuel(refuel: Refuel): String {
        val id = refuel.id.ifBlank { "refuel-${nextId++}" }
        refuelState.update { all -> all.filterNot { it.id == id } + refuel.copy(id = id) }
        return id
    }

    override suspend fun saveExpense(expense: Expense): String {
        val id = expense.id.ifBlank { "expense-${nextId++}" }
        expenseState.update { all -> all.filterNot { it.id == id } + expense.copy(id = id) }
        return id
    }

    override suspend fun deleteRefuel(id: String): DeletedEntry {
        refuelState.value.firstOrNull { it.id == id }?.let { deletedRefuels[id] = it }
        refuelState.update { all -> all.filterNot { it.id == id } }
        return DeletedEntry(DeletedEntry.Kind.Refuel, id, 0)
    }

    override suspend fun deleteExpense(id: String): DeletedEntry {
        expenseState.value.firstOrNull { it.id == id }?.let { deletedExpenses[id] = it }
        expenseState.update { all -> all.filterNot { it.id == id } }
        return DeletedEntry(DeletedEntry.Kind.Expense, id, 0)
    }

    override suspend fun undoDelete(token: DeletedEntry) {
        when (token.kind) {
            DeletedEntry.Kind.Refuel -> deletedRefuels.remove(token.id)?.let { r -> refuelState.update { it + r } }
            DeletedEntry.Kind.Expense -> deletedExpenses.remove(token.id)?.let { e -> expenseState.update { it + e } }
        }
    }
}

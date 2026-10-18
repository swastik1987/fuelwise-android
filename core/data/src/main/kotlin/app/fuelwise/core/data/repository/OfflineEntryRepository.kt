package app.fuelwise.core.data.repository

import app.fuelwise.core.data.DeviceIdProvider
import app.fuelwise.core.data.mapper.toEntity
import app.fuelwise.core.data.mapper.toModel
import app.fuelwise.core.database.dao.EntryDao
import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class OfflineEntryRepository @Inject constructor(
    private val dao: EntryDao,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val deviceIds: DeviceIdProvider,
) : EntryRepository {
    override fun observeRefuels(vehicleId: String): Flow<List<Refuel>> =
        dao.observeRefuels(vehicleId).map { rows -> rows.map { it.toModel() } }

    override fun observeAllRefuels(): Flow<List<Refuel>> = dao.observeAllRefuels().map { rows ->
        rows.map { it.toModel() }
    }

    override fun observeRefuel(id: String): Flow<Refuel?> = dao.observeRefuel(id).map { it?.toModel() }

    override fun observeExpenses(vehicleId: String): Flow<List<Expense>> =
        dao.observeExpenses(vehicleId).map { rows -> rows.map { it.toModel() } }

    override fun observeAllExpenses(): Flow<List<Expense>> = dao.observeAllExpenses().map { rows ->
        rows.map { it.toModel() }
    }

    override fun observeExpense(id: String): Flow<Expense?> = dao.observeExpense(id).map { it?.toModel() }

    override fun observeRecentPlazas(): Flow<List<String>> = dao.observeRecentPlazas(RECENT_PLAZAS)

    override suspend fun saveRefuel(refuel: Refuel): String {
        val id = refuel.id.ifBlank { ids.newId() }
        val existing = if (refuel.id.isBlank()) null else dao.observeRefuel(refuel.id).first()
        // An edited entry stays in its import batch, so undoing the import still removes it.
        val entity = refuel.copy(id = id).toEntity(syncFor(existing?.sync?.createdAt))
        dao.upsertRefuel(entity.copy(importBatchId = existing?.importBatchId))
        return id
    }

    override suspend fun saveExpense(expense: Expense): String {
        val id = expense.id.ifBlank { ids.newId() }
        val existing = if (expense.id.isBlank()) null else dao.observeExpense(expense.id).first()
        val entity = expense.copy(id = id).toEntity(syncFor(existing?.sync?.createdAt))
        dao.upsertExpense(entity.copy(importBatchId = existing?.importBatchId))
        return id
    }

    override suspend fun deleteRefuel(id: String): DeletedEntry {
        val at = clock.millis()
        dao.tombstoneRefuel(id, at, deviceIds.deviceId())
        return DeletedEntry(DeletedEntry.Kind.Refuel, id, at)
    }

    override suspend fun deleteExpense(id: String): DeletedEntry {
        val at = clock.millis()
        dao.tombstoneExpense(id, at, deviceIds.deviceId())
        return DeletedEntry(DeletedEntry.Kind.Expense, id, at)
    }

    override suspend fun undoDelete(token: DeletedEntry) {
        val device = deviceIds.deviceId()
        when (token.kind) {
            DeletedEntry.Kind.Refuel -> dao.restoreRefuel(token.id, token.deletedAtUtcMs, clock.millis(), device)
            DeletedEntry.Kind.Expense -> dao.restoreExpense(token.id, token.deletedAtUtcMs, clock.millis(), device)
        }
    }

    private suspend fun syncFor(createdAt: Long?): SyncMeta {
        val now = clock.millis()
        return SyncMeta(createdAt = createdAt ?: now, updatedAt = now, deviceId = deviceIds.deviceId())
    }

    private companion object {
        const val RECENT_PLAZAS = 8
    }
}

package app.fuelwise.core.data.repository

import app.fuelwise.core.data.mapper.toEntity
import app.fuelwise.core.database.dao.ImportDao
import app.fuelwise.core.database.model.ImportBatchEntity
import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.domain.importing.ColumnMapping
import app.fuelwise.core.domain.importing.ImportPlan
import app.fuelwise.core.model.ImportBatch
import app.fuelwise.core.model.ImportSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** File imports (PRD F-13.2): commit a checked plan in one transaction, list past imports, undo one. */
interface ImportRepository {
    fun observeBatches(): Flow<List<ImportBatch>>

    /** Saves every entry of [plan] under a new batch, all or nothing. */
    suspend fun commit(plan: ImportPlan, source: ImportSource, fileName: String?): ImportBatch

    /** Removes the batch and all its entries (tombstones, so backups and sync see the delete). */
    suspend fun undo(batchId: String)

    suspend fun savedMapping(signature: String): ColumnMapping?

    suspend fun saveMapping(signature: String, mapping: ColumnMapping)
}

internal class OfflineImportRepository @Inject constructor(
    private val dao: ImportDao,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
    private val preferences: PreferencesDataSource,
) : ImportRepository {
    override fun observeBatches(): Flow<List<ImportBatch>> = dao.observeBatches(RECENT).map { rows ->
        rows.map { row ->
            ImportBatch(
                id = row.id,
                source = ImportSource.entries.firstOrNull { it.name == row.source } ?: ImportSource.Spreadsheet,
                fileName = row.fileName,
                importedAtUtcMs = row.importedAt,
                refuelCount = row.refuelCount,
                expenseCount = row.expenseCount,
            )
        }
    }

    override suspend fun commit(plan: ImportPlan, source: ImportSource, fileName: String?): ImportBatch {
        val batchId = ids.newId()
        val stamp = sync.stamp()
        val refuels = plan.refuels.map { it.copy(id = ids.newId()).toEntity(stamp).copy(importBatchId = batchId) }
        val expenses = plan.expenses.map { it.copy(id = ids.newId()).toEntity(stamp).copy(importBatchId = batchId) }
        val batch = ImportBatch(batchId, source, fileName, stamp.createdAt, refuels.size, expenses.size)
        dao.commit(
            ImportBatchEntity(
                id = batchId,
                source = source.name,
                fileName = fileName,
                importedAt = stamp.createdAt,
                refuelCount = refuels.size,
                expenseCount = expenses.size,
                sync = stamp,
            ),
            refuels,
            expenses,
        )
        return batch
    }

    override suspend fun undo(batchId: String) {
        dao.undo(batchId, sync.now(), sync.device())
    }

    override suspend fun savedMapping(signature: String): ColumnMapping? =
        preferences.preferences.first().importMappingsMap[signature]?.let(ColumnMapping::decode)

    override suspend fun saveMapping(signature: String, mapping: ColumnMapping) {
        preferences.update { importMappings.put(signature, mapping.encode()) }
    }

    private companion object {
        const val RECENT = 10
    }
}

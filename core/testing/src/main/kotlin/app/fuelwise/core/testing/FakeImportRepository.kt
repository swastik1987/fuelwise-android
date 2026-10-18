package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.ImportRepository
import app.fuelwise.core.domain.importing.ColumnMapping
import app.fuelwise.core.domain.importing.ImportPlan
import app.fuelwise.core.model.ImportBatch
import app.fuelwise.core.model.ImportSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [ImportRepository]: records committed plans and remembered mappings. */
class FakeImportRepository(initial: List<ImportBatch> = emptyList()) : ImportRepository {
    private val batches = MutableStateFlow(initial)
    private val mappings = mutableMapOf<String, ColumnMapping>()
    val committed = mutableListOf<ImportPlan>()
    val undone = mutableListOf<String>()

    override fun observeBatches(): Flow<List<ImportBatch>> = batches

    override suspend fun commit(plan: ImportPlan, source: ImportSource, fileName: String?): ImportBatch {
        committed += plan
        val batch = ImportBatch("batch-${committed.size}", source, fileName, 0L, plan.refuels.size, plan.expenses.size)
        batches.update { listOf(batch) + it }
        return batch
    }

    override suspend fun undo(batchId: String) {
        undone += batchId
        batches.update { all -> all.filter { it.id != batchId } }
    }

    override suspend fun savedMapping(signature: String): ColumnMapping? = mappings[signature]

    override suspend fun saveMapping(signature: String, mapping: ColumnMapping) {
        mappings[signature] = mapping
    }
}

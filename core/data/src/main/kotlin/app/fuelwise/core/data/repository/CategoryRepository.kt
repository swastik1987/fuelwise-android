package app.fuelwise.core.data.repository

import app.fuelwise.core.data.mapper.enumValueOrNull
import app.fuelwise.core.database.dao.CategoryDao
import app.fuelwise.core.database.model.CategoryEntity
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.SystemCategory
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What deleting a category did (PRD F-04.2: "not deletable if used"). */
enum class CategoryRemoval { Deleted, HiddenBecauseUsed, NotAllowed }

interface CategoryRepository {
    /** Expense categories in display order, hidden ones included (the picker filters them). */
    fun observeCategories(): Flow<List<Category>>

    /** A user's own category (PRD F-04.2), counted towards cost/km like most presets. Returns its id. */
    suspend fun addCustom(name: String, icon: String): String

    /** A new name; null puts a system preset back to its translated name. */
    suspend fun rename(id: String, name: String?)

    suspend fun setHidden(id: String, hidden: Boolean)

    /** Deletes an unused custom category; a used one is hidden instead, and presets are never deleted. */
    suspend fun remove(id: String): CategoryRemoval
}

internal class OfflineCategoryRepository @Inject constructor(
    private val dao: CategoryDao,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> = dao.observeCategories().map { rows ->
        rows.map { row ->
            Category(
                id = row.id,
                systemKey = row.systemKey?.let { enumValueOrNull<SystemCategory>(it) },
                customName = row.customName,
                isHidden = row.isHidden,
                includeInCostPerKm = row.includeInCostPerKm,
                sortOrder = row.sortOrder,
                icon = row.icon,
            )
        }
    }

    override suspend fun addCustom(name: String, icon: String): String {
        val id = ids.newId()
        dao.upsert(
            CategoryEntity(
                id = id,
                systemKey = null,
                customName = name.trim().take(MAX_NAME),
                icon = icon,
                // Categories stay neutral in the UI (design.md): no per-category colour.
                color = null,
                isHidden = false,
                includeInCostPerKm = true,
                sortOrder = dao.maxSortOrder() + 1,
                sync = sync.stamp(),
            ),
        )
        return id
    }

    override suspend fun rename(id: String, name: String?) {
        val row = dao.category(id) ?: return
        val clean = name?.trim()?.take(MAX_NAME)?.ifEmpty { null }
        // A custom category always keeps a name.
        if (clean == null && row.systemKey == null) return
        dao.upsert(row.copy(customName = clean, sync = sync.stamp(row.sync.createdAt)))
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        val row = dao.category(id) ?: return
        dao.upsert(row.copy(isHidden = hidden, sync = sync.stamp(row.sync.createdAt)))
    }

    override suspend fun remove(id: String): CategoryRemoval {
        val row = dao.category(id) ?: return CategoryRemoval.NotAllowed
        if (row.systemKey != null) return CategoryRemoval.NotAllowed
        return if (dao.usage(id) > 0) {
            setHidden(id, true)
            CategoryRemoval.HiddenBecauseUsed
        } else {
            // A tombstone, never a hard delete (CLAUDE.md rule 3).
            dao.tombstoneCustom(id, sync.now(), sync.device())
            CategoryRemoval.Deleted
        }
    }

    private companion object {
        const val MAX_NAME = 30
    }
}

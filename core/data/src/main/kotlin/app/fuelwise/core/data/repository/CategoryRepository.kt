package app.fuelwise.core.data.repository

import app.fuelwise.core.data.mapper.enumValueOrNull
import app.fuelwise.core.database.dao.CategoryDao
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.SystemCategory
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CategoryRepository {
    /** Expense categories in display order, hidden ones included (the picker filters them). */
    fun observeCategories(): Flow<List<Category>>
}

internal class OfflineCategoryRepository @Inject constructor(private val dao: CategoryDao) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> = dao.observeCategories().map { rows ->
        rows.map { row ->
            Category(
                id = row.id,
                systemKey = row.systemKey?.let { enumValueOrNull<SystemCategory>(it) },
                customName = row.customName,
                isHidden = row.isHidden,
                includeInCostPerKm = row.includeInCostPerKm,
                sortOrder = row.sortOrder,
            )
        }
    }
}

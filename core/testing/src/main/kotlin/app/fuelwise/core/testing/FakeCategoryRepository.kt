package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.SystemCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The seeded system categories, in seed order. */
class FakeCategoryRepository(private val categories: List<Category> = SYSTEM) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> = flowOf(categories)

    companion object {
        val SYSTEM = SystemCategory.entries.mapIndexed { i, c ->
            Category(c.id, c, null, false, c.includeInCostPerKm, i)
        }
    }
}

package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.CategoryRemoval
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.SystemCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory categories: the system presets by default. [used] ids behave as if expenses use them. */
class FakeCategoryRepository(categories: List<Category> = SYSTEM, private val used: Set<String> = emptySet()) :
    CategoryRepository {
    val state = MutableStateFlow(categories)
    private var next = 0

    override fun observeCategories(): Flow<List<Category>> = state

    override suspend fun addCustom(name: String, icon: String): String {
        val id = "custom-${++next}"
        state.update { it + Category(id, null, name, false, true, it.size, icon) }
        return id
    }

    override suspend fun rename(id: String, name: String?) = edit(id) { it.copy(customName = name) }

    override suspend fun setHidden(id: String, hidden: Boolean) = edit(id) { it.copy(isHidden = hidden) }

    override suspend fun remove(id: String): CategoryRemoval {
        val category = state.value.firstOrNull { it.id == id } ?: return CategoryRemoval.NotAllowed
        return when {
            category.isSystem -> CategoryRemoval.NotAllowed
            id in used -> CategoryRemoval.HiddenBecauseUsed.also { setHidden(id, true) }
            else -> CategoryRemoval.Deleted.also { state.update { all -> all.filterNot { it.id == id } } }
        }
    }

    private fun edit(id: String, change: (Category) -> Category) =
        state.update { all -> all.map { if (it.id == id) change(it) else it } }

    companion object {
        val SYSTEM = SystemCategory.entries.mapIndexed { i, c ->
            Category(c.id, c, null, false, c.includeInCostPerKm, i)
        }
    }
}

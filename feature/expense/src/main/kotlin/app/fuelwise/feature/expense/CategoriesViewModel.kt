package app.fuelwise.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.CategoryRemoval
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(val loading: Boolean = true, val categories: List<Category> = emptyList())

sealed interface CategoriesEvent {
    /** A custom category that expenses use was hidden rather than deleted. */
    data object KeptBecauseUsed : CategoriesEvent

    data object Deleted : CategoriesEvent
}

/**
 * Expense categories (PRD F-04.2): add your own (name and icon), rename or hide any, delete an unused custom one.
 * Hidden categories drop out of the expense picker; past expenses keep theirs.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(private val categories: CategoryRepository) : ViewModel() {
    val state: StateFlow<CategoriesUiState> = categories.observeCategories()
        .map { CategoriesUiState(loading = false, categories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoriesUiState())

    private val eventChannel = Channel<CategoriesEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun add(name: String, icon: String) {
        if (name.isBlank()) return
        viewModelScope.launch { categories.addCustom(name, icon) }
    }

    /** A blank name puts a preset back to its own name; a custom one keeps its old name. */
    fun rename(id: String, name: String) {
        viewModelScope.launch { categories.rename(id, name.ifBlank { null }) }
    }

    fun setHidden(id: String, hidden: Boolean) {
        viewModelScope.launch { categories.setHidden(id, hidden) }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            when (categories.remove(id)) {
                CategoryRemoval.Deleted -> eventChannel.send(CategoriesEvent.Deleted)
                CategoryRemoval.HiddenBecauseUsed -> eventChannel.send(CategoriesEvent.KeptBecauseUsed)
                CategoryRemoval.NotAllowed -> Unit
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package app.fuelwise.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.model.Cities
import app.fuelwise.core.model.City
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val query: String = "",
    val results: List<City> = emptyList(),
    val popular: List<City> = Cities.popular,
    val selected: City? = null,
)

/** "Bring your history" (design.md screen 3). Imports arrive with F-13 (M10); the city choice is saved now. */
@HiltViewModel
class HistoryViewModel @Inject constructor(private val userData: UserDataRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<HistoryUiState> = combine(query, userData.userData) { q, data ->
        HistoryUiState(query = q, results = Cities.search(q), selected = Cities.byId(data.homeCityId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCitySelected(city: City) {
        query.value = ""
        viewModelScope.launch { userData.setHomeCity(city.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

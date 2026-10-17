package app.fuelwise.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data object NoVehicles : HomeUiState

    data class Ready(val vehicle: Vehicle, val vehicles: List<Vehicle>) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(vehicles: VehicleRepository, private val userData: UserDataRepository) :
    ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(vehicles.observeVehicles(), userData.userData) { list, data ->
        // A deleted or archived selection falls back to the first vehicle.
        val selected = list.firstOrNull { it.id == data.selectedVehicleId } ?: list.firstOrNull()
        if (selected == null) HomeUiState.NoVehicles else HomeUiState.Ready(selected, list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState.Loading)

    fun selectVehicle(id: String?) {
        viewModelScope.launch { userData.setSelectedVehicle(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

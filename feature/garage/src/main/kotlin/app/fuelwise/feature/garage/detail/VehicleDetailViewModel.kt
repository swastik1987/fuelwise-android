package app.fuelwise.feature.garage.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.model.Vehicle
import app.fuelwise.feature.garage.VehicleDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VehicleDetailUiState {
    data object Loading : VehicleDetailUiState

    /** The vehicle was deleted (here or elsewhere) while the screen was open. */
    data object Gone : VehicleDetailUiState

    data class Ready(val vehicle: Vehicle) : VehicleDetailUiState
}

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
) : ViewModel() {
    private val vehicleId = savedStateHandle.toRoute<VehicleDetailRoute>().vehicleId

    val uiState: StateFlow<VehicleDetailUiState> = vehicles.observeVehicle(vehicleId)
        .map { v -> if (v == null) VehicleDetailUiState.Gone else VehicleDetailUiState.Ready(v) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VehicleDetailUiState.Loading)

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { vehicles.setArchived(vehicleId, archived) }
    }

    /** Delete needs the vehicle's name typed back exactly (PRD F-01 AC); case and outer spaces don't matter. */
    fun confirmationMatches(typed: String, vehicle: Vehicle): Boolean =
        typed.trim().equals(vehicle.name.trim(), ignoreCase = true)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

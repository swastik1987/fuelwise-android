package app.fuelwise.feature.garage.detail

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.model.Vehicle
import app.fuelwise.feature.garage.VehicleDetailRoute
import app.fuelwise.feature.garage.photo.VehiclePhotoLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VehicleDetailUiState {
    data object Loading : VehicleDetailUiState

    /** The vehicle was deleted (here or elsewhere) while the screen was open. */
    data object Gone : VehicleDetailUiState

    /** [photo] is the header photo (design.md screen 22), when the vehicle has one. */
    data class Ready(val vehicle: Vehicle, val photo: ImageBitmap? = null) : VehicleDetailUiState
}

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    photos: VehiclePhotoLoader,
) : ViewModel() {
    private val vehicleId = savedStateHandle.toRoute<VehicleDetailRoute>().vehicleId

    val uiState: StateFlow<VehicleDetailUiState> = combine(
        vehicles.observeVehicle(vehicleId),
        photos.observe(HEADER_PX).onStart { emit(emptyMap()) },
    ) { v, byVehicle -> if (v == null) VehicleDetailUiState.Gone else VehicleDetailUiState.Ready(v, byVehicle[v.id]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VehicleDetailUiState.Loading)

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { vehicles.setArchived(vehicleId, archived) }
    }

    /** Delete needs the vehicle's name typed back exactly (PRD F-01 AC); case and outer spaces don't matter. */
    fun confirmationMatches(typed: String, vehicle: Vehicle): Boolean =
        typed.trim().equals(vehicle.name.trim(), ignoreCase = true)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val HEADER_PX = 1080
    }
}

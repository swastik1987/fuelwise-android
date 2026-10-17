package app.fuelwise.feature.garage.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.DeletedVehicle
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.model.Vehicle
import app.fuelwise.feature.garage.PENDING_DELETE_VEHICLE_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GarageUiState {
    data object Loading : GarageUiState

    data class Ready(val active: List<Vehicle>, val archived: List<Vehicle>) : GarageUiState
}

sealed interface GarageEvent {
    data class VehicleDeleted(val name: String, val token: DeletedVehicle) : GarageEvent
}

@HiltViewModel
class GarageViewModel @Inject constructor(
    private val vehicles: VehicleRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val uiState: StateFlow<GarageUiState> = vehicles.observeVehicles(includeArchived = true)
        .map { all -> GarageUiState.Ready(all.filterNot { it.isArchived }, all.filter { it.isArchived }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GarageUiState.Loading)

    private val eventChannel = Channel<GarageEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(PENDING_DELETE_VEHICLE_ID, null).filterNotNull().collect { id ->
                savedStateHandle[PENDING_DELETE_VEHICLE_ID] = null
                val name = vehicles.observeVehicle(id).first()?.name ?: return@collect
                eventChannel.send(GarageEvent.VehicleDeleted(name, vehicles.delete(id)))
            }
        }
    }

    fun undoDelete(token: DeletedVehicle) {
        viewModelScope.launch { vehicles.undoDelete(token) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

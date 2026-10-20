package app.fuelwise.feature.settings.units

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.Vehicle
import app.fuelwise.feature.settings.UnitsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One vehicle in the per-vehicle list; [override] is null when it follows the default. */
data class VehicleUnits(val vehicle: Vehicle, val override: DisplayUnits?)

data class UnitsUiState(
    val loading: Boolean = true,
    val default: DisplayUnits = DisplayUnits.INDIA,
    /** Set when editing one vehicle's units; null on the app-wide screen. */
    val vehicle: VehicleUnits? = null,
    /** Active vehicles, for the per-vehicle list on the app-wide screen. */
    val vehicles: List<VehicleUnits> = emptyList(),
) {
    /** What the rows show: the vehicle's own units, or the default it follows. */
    val shown: DisplayUnits get() = vehicle?.override ?: default
}

/** Units (PRD F-18): app-wide defaults, or one vehicle's own units when the route names a vehicle. */
@HiltViewModel
class UnitsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userData: UserDataRepository,
    vehicles: VehicleRepository,
) : ViewModel() {
    private val vehicleId: String? = savedStateHandle.toRoute<UnitsRoute>().vehicleId

    val state: StateFlow<UnitsUiState> = combine(userData.userData, vehicles.observeVehicles()) { data, list ->
        val rows = list.filterNot { it.isArchived }.map { VehicleUnits(it, data.units.perVehicle[it.id]) }
        UnitsUiState(
            loading = false,
            default = data.units.default,
            vehicle = vehicleId?.let { id ->
                list.firstOrNull { it.id == id }?.let { VehicleUnits(it, data.units.perVehicle[id]) }
            },
            vehicles = if (vehicleId == null) rows else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UnitsUiState())

    /** Changes the default, or this vehicle's units (which starts an override from the default). */
    fun update(transform: (DisplayUnits) -> DisplayUnits) {
        val next = transform(state.value.shown)
        viewModelScope.launch { userData.setUnits(vehicleId, next) }
    }

    /** Vehicle screen only: follow the default again, or start its own units from a copy of the default. */
    fun setOwnUnits(own: Boolean) {
        val id = vehicleId ?: return
        viewModelScope.launch { userData.setUnits(id, if (own) state.value.default else null) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

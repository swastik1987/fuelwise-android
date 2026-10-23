package app.fuelwise.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import app.fuelwise.core.data.repository.CarLink
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.location.AutoTrips
import app.fuelwise.core.location.CarLinker
import app.fuelwise.core.model.VehicleType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A vehicle in the optional car Bluetooth step; [link] when already linked. */
data class SetupCar(val vehicleId: String, val name: String, val link: CarLink?)

/**
 * Automatic trips setup (design.md screen 32, PRD F-21.3). The screen walks the disclosure and the permission steps;
 * this only turns the feature on at the end, in full or one-tap mode, and registers driving detection.
 */
@HiltViewModel
class AutoTripSetupViewModel @Inject constructor(
    private val settings: AutoTripSettingsRepository,
    private val autoTrips: AutoTrips,
    private val carLinker: CarLinker,
    vehicles: VehicleRepository,
) : ViewModel() {
    /** Vehicles with a car kit to link (not two-wheelers), with their linked car's name, for the optional step. */
    val cars: StateFlow<List<SetupCar>> = combine(vehicles.observeVehicles(), settings.settings) { list, s ->
        list.filter { it.type != VehicleType.Scooter && it.type != VehicleType.Motorcycle }
            .map { SetupCar(it.id, it.name, s.carLinks[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun linkCar(vehicleId: String, link: CarLink) {
        viewModelScope.launch { carLinker.linkCar(vehicleId, link) }
    }

    /** The vehicle named in the one-tap notification preview, if there's exactly one to name. */
    val previewVehicle: StateFlow<String?> = vehicles.observeVehicles()
        .map { list -> list.singleOrNull()?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val doneChannel = Channel<Unit>(Channel.CONFLATED)

    /** Fires once setup has saved; the screen closes. */
    val done = doneChannel.receiveAsFlow()

    /** [oneTap]: "Allow all the time" was refused and the user chose to start trips with one tap instead. */
    fun finish(oneTap: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(true, oneTap)
            autoTrips.syncRegistration()
            doneChannel.send(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

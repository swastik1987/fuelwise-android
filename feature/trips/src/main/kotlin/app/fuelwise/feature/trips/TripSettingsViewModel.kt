package app.fuelwise.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import app.fuelwise.core.data.repository.CarLink
import app.fuelwise.core.data.repository.LocationPrivacyRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.AutoTripPause
import app.fuelwise.core.domain.trip.RouteRetention
import app.fuelwise.core.domain.trip.autoTripPauseEnd
import app.fuelwise.core.location.CarLinker
import app.fuelwise.core.model.VehicleType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripSettingsUiState(
    val loading: Boolean = true,
    /** Automatic trips on this phone (PRD F-21.3); null when the feature isn't offered. */
    val autoTrips: AutoTripsUi? = null,
    /** Vehicles that can have car Bluetooth (not two-wheelers), with their linked car. */
    val cars: List<CarRow> = emptyList(),
    /** Location privacy (PRD F-21.6). */
    val routeRetention: RouteRetention = RouteRetention.DEFAULT,
    val privatePlaceCount: Int = 0,
)

/** The "Automatic trips" row: off, on, one-tap, or paused until [pausedUntilUtcMs] ([Long.MAX_VALUE] = until on). */
data class AutoTripsUi(val enabled: Boolean, val oneTap: Boolean, val pausedUntilUtcMs: Long?)

/** One vehicle's car Bluetooth row; [linked] is false when no car is linked, [carName] when the system gave one. */
data class CarRow(val vehicleId: String, val vehicleName: String, val linked: Boolean, val carName: String?)

/**
 * Trip settings inside Trips (owner decision 12 Nov 2026, user feedback; PRD F-21.3, F-21.6, F-21.7): automatic
 * trips, car Bluetooth per vehicle and location privacy, moved here from Settings. Orchestration only.
 */
@HiltViewModel
class TripSettingsViewModel @Inject constructor(
    private val autoTrips: AutoTripSettingsRepository,
    private val privacy: LocationPrivacyRepository,
    vehicles: VehicleRepository,
    private val carLinker: CarLinker,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    val state: StateFlow<TripSettingsUiState> = combine(
        autoTrips.settings,
        privacy.retention,
        privacy.privatePlaces,
        vehicles.observeVehicles(),
    ) { auto, retention, places, list ->
        TripSettingsUiState(
            loading = false,
            autoTrips = auto.takeIf { it.available }?.let { s ->
                AutoTripsUi(
                    enabled = s.enabled,
                    oneTap = s.oneTap,
                    // A pause that has ended shows as on.
                    pausedUntilUtcMs = s.pausedUntilUtcMs?.takeIf { it > clock.millis() },
                )
            },
            cars = if (!auto.available) {
                emptyList()
            } else {
                list.filter { it.type != VehicleType.Scooter && it.type != VehicleType.Motorcycle }.map { v ->
                    val link = auto.carLinks[v.id]
                    CarRow(v.id, v.name, linked = link != null, carName = link?.name)
                }
            },
            routeRetention = retention,
            privatePlaceCount = places.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripSettingsUiState())

    /** "Pause automatic trips" (PRD F-21.3 controls). */
    fun pauseAutoTrips(pause: AutoTripPause) = launch {
        autoTrips.pauseUntil(autoTripPauseEnd(pause, clock.millis(), zoneId))
    }

    fun resumeAutoTrips() = launch { autoTrips.pauseUntil(null) }

    /** Off: driving detection is unregistered at once (AutoTrips follows the setting). */
    fun turnOffAutoTrips() = launch { autoTrips.setEnabled(false) }

    fun linkCar(vehicleId: String, link: CarLink) = launch { carLinker.linkCar(vehicleId, link) }

    fun unlinkCar(vehicleId: String) = launch { carLinker.unlinkCar(vehicleId) }

    fun setRouteRetention(retention: RouteRetention) = launch { privacy.setRetention(retention) }

    /** "Delete all location history" (PRD F-21.6): saved routes and private places; trips and everything else stay. */
    fun deleteLocationHistory() = launch { privacy.deleteAllLocationHistory() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

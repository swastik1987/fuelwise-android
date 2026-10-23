package app.fuelwise.core.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.fuelwise.core.data.repository.AutoTripSettings
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import app.fuelwise.core.data.repository.CarLink
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.assignAutoTripVehicle
import app.fuelwise.core.model.TripSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts the recording service for an automatic trip, from a background event Android allows it for. */
interface AutoTripStarter {
    /** [vehicleId]: the car whose Bluetooth connected; otherwise the vehicle is worked out from habit. */
    fun startAutomaticRecording(vehicleId: String? = null)
}

/** Links a vehicle to its car's Bluetooth (PRD F-21.3); the vehicle editor and setup use it. */
interface CarLinker {
    suspend fun linkCar(vehicleId: String, link: CarLink)

    suspend fun unlinkCar(vehicleId: String)
}

/**
 * Automatic trips (PRD F-21.3): what a detected drive does. With "Allow all the time" it records on its own;
 * without it (or when the user chose one-tap starts) it posts "Start trip?", and a tap starts an ordinary GPS trip
 * with while-in-use access. Nothing happens while the feature is off, paused, or a trip is already recording.
 */
@Singleton
class AutoTrips @Inject constructor(
    private val settings: AutoTripSettingsRepository,
    private val detection: DriveDetection,
    private val cars: CarPresence,
    private val recorder: TripRecorder,
    private val vehicles: VehicleRepository,
    private val userData: UserDataRepository,
    private val trips: TripRepository,
    private val starter: AutoTripStarter,
    private val prompts: TripPrompts,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : CarLinker {
    /** Keeps activity detection registered exactly while automatic trips are on. Call once, at app start. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.settings.map { (it.available && it.enabled) to it.carLinks.values.toList() }
                .distinctUntilChanged()
                .collect { (on, links) -> sync(on, links) }
        }
    }

    /** After a reboot, an app update or a permission change: registrations don't survive those. */
    suspend fun syncRegistration() {
        val s = settings.settings.first()
        sync(s.available && s.enabled, s.carLinks.values.toList())
    }

    private fun sync(on: Boolean, links: List<CarLink>) {
        if (on && detection.hasPermission) detection.register() else detection.unregister()
        // Observing is idempotent; it also restores presence after a reboot. Linked cars stay linked while off.
        if (on) links.forEach(cars::observe)
    }

    /** Links [vehicleId] to the car picked in the system chooser (PRD F-21.3), replacing any earlier link. */
    override suspend fun linkCar(vehicleId: String, link: CarLink) {
        val s = settings.settings.first()
        s.carLinks[vehicleId]?.takeIf { it.address != link.address }?.let(cars::forget)
        // One car, one vehicle: a car linked elsewhere moves here.
        s.vehicleFor(link.address)?.takeIf { it != vehicleId }?.let { settings.setCarLink(it, null) }
        settings.setCarLink(vehicleId, link)
        if (s.available && s.enabled) cars.observe(link)
    }

    override suspend fun unlinkCar(vehicleId: String) {
        settings.settings.first().carLinks[vehicleId]?.let(cars::forget)
        settings.setCarLink(vehicleId, null)
    }

    /** A linked car's Bluetooth connected: start a trip for that vehicle, as a drive does. */
    suspend fun onCarConnected(address: String?, associationId: Int) {
        val now = clock.millis()
        val s = settings.settings.first()
        val vehicleId = s.linkedVehicle(address, associationId) ?: return
        if (!s.isWatching(now) || recorder.state.value is RecordingState.Active) return
        if (s.oneTap || !detection.hasBackgroundLocation) {
            prompts.startTripPrompt()
        } else {
            starter.startAutomaticRecording(vehicleId)
        }
    }

    /** A linked car disconnected: its automatic trip ends. Another vehicle's trip isn't touched. */
    suspend fun onCarDisconnected(address: String?, associationId: Int) {
        val vehicleId = settings.settings.first().linkedVehicle(address, associationId) ?: return
        if ((recorder.state.value as? RecordingState.Active)?.vehicleId ==
            vehicleId
        ) {
            recorder.stop(onlyAutomatic = true)
        }
    }

    private fun AutoTripSettings.linkedVehicle(address: String?, associationId: Int): String? =
        carLinks.entries.firstOrNull { associationId > 0 && it.value.associationId == associationId }?.key
            ?: address?.let(::vehicleFor)

    /** Driving detection says the user got into a vehicle. */
    suspend fun onDriving() {
        val now = clock.millis()
        settings.recordActivityEvent(now)
        val s = settings.settings.first()
        if (!s.isWatching(now) || recorder.state.value is RecordingState.Active) return
        if (s.oneTap || !detection.hasBackgroundLocation) {
            prompts.startTripPrompt()
        } else {
            starter.startAutomaticRecording()
        }
    }

    /** Driving detection says the user left the vehicle: an automatic trip ends here. */
    suspend fun onLeftVehicle() {
        settings.recordActivityEvent(clock.millis())
        recorder.stop(onlyAutomatic = true)
    }

    /**
     * Called by the recording service: picks the vehicle (PRD F-21.3: Bluetooth, then the user's habit at this time
     * of day, then the selected vehicle) and starts recording. Null without a vehicle.
     */
    suspend fun startAutomaticTrip(bluetoothVehicleId: String? = null): String? {
        val active = vehicles.observeVehicles().first()
        val vehicleId = assignAutoTripVehicle(
            bluetoothVehicleId = bluetoothVehicleId,
            start = ZonedDateTime.now(clock).withZoneSameInstant(zoneId),
            history = trips.observeTrips().first(),
            activeVehicleIds = active.mapTo(LinkedHashSet()) { it.id },
            defaultVehicleId = userData.userData.first().selectedVehicleId,
        ) ?: return null
        return recorder.start(vehicleId, TripSource.Auto)
    }
}

/** Sends the service its "start an automatic trip" action. */
internal class ServiceAutoTripStarter @Inject constructor(@param:ApplicationContext private val context: Context) :
    AutoTripStarter {
    override fun startAutomaticRecording(vehicleId: String?) {
        // Allowed from the background: activity-transition events and companion-device presence are exemptions.
        ContextCompat.startForegroundService(
            context,
            Intent(context, TripRecordingService::class.java)
                .setAction(TripRecordingService.ACTION_START_AUTO)
                .putExtra(TripRecordingService.EXTRA_VEHICLE_ID, vehicleId),
        )
    }
}

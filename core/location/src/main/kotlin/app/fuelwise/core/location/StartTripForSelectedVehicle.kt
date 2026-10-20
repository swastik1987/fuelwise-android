package app.fuelwise.core.location

import android.content.Context
import android.content.Intent
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Starts a GPS trip for the vehicle the switcher selected (or the first one); null without a vehicle. */
class StartTripForSelectedVehicle @Inject constructor(
    private val vehicles: VehicleRepository,
    private val userData: UserDataRepository,
    private val recorder: TripRecorder,
) {
    suspend operator fun invoke(): String? {
        val all = vehicles.observeVehicles().first()
        val selected = userData.userData.first().selectedVehicleId
        val vehicle = all.firstOrNull { it.id == selected } ?: all.firstOrNull() ?: return null
        return recorder.start(vehicle.id)
    }
}

/**
 * Intents for surfaces outside the app's screens (home-screen widgets, PRD F-16) to drive the recorder. Send them
 * only from a user's tap on a widget: that is one of the few ways Android lets a background app start a `location`
 * foreground service with while-in-use access. Start only when location is already granted
 * ([TripRecorder.hasPermission]);
 * otherwise open the app's Trips screen, which asks first.
 */
object TripRecordingIntents {
    fun start(context: Context): Intent =
        Intent(context, TripRecordingService::class.java).setAction(TripRecordingService.ACTION_START)

    fun stop(context: Context): Intent =
        Intent(context, TripRecordingService::class.java).setAction(TripRecordingService.ACTION_STOP)
}

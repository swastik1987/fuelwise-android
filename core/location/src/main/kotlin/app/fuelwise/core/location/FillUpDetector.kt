package app.fuelwise.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FillUpPromptRepository
import app.fuelwise.core.data.repository.StationRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.trip.FillNeed
import app.fuelwise.core.domain.trip.FillUpPrefill
import app.fuelwise.core.domain.trip.STATION_RADIUS_M
import app.fuelwise.core.domain.trip.findStops
import app.fuelwise.core.domain.trip.shouldPromptFillUp
import app.fuelwise.core.domain.trip.stationForStop
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.notifications.NotificationChannels
import app.fuelwise.core.notifications.NotificationRoutes
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Runs after a recorded trip is saved (PRD F-21.4). */
interface FillUpCheck {
    suspend fun afterTrip(trip: Trip, points: List<TrackPoint>)
}

/**
 * Fill-up detection (PRD F-21.4): a stop of 2–15 minutes within 40 m of a station from the bundled list (D16) gets one
 * "Filled up at HP? Tap to log." after the trip. Matched on the phone; never saves anything itself. Only while
 * automatic trips are on: it's part of that one declared feature (D13).
 */
internal class FillUpDetector @Inject constructor(
    private val settings: AutoTripSettingsRepository,
    private val stations: StationRepository,
    private val prompts: FillUpPromptRepository,
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val notifier: FillUpNotifier,
    private val clock: Clock,
) : FillUpCheck {
    override suspend fun afterTrip(trip: Trip, points: List<TrackPoint>) {
        val s = settings.settings.first()
        if (!s.available || !s.enabled) return
        val vehicle = vehicles.observeVehicle(trip.vehicleId).first() ?: return
        val state = prompts.state.first()
        val now = clock.millis()
        val logged = entries.observeRefuels(vehicle.id).first().map { it.dateTimeUtcMs }
        // The last stop that qualifies: with several, the latest is the likelier fill-up to be asked about.
        val (stop, station) = findStops(points).asReversed().firstNotNullOfOrNull { stop ->
            if (!shouldPromptFillUp(stop.startUtcMs, logged, state.lastPromptUtcMs[vehicle.id], now)) {
                null
            } else {
                stationForStop(
                    stations.near(stop.lat, stop.lng, STATION_RADIUS_M),
                    vehicle.fillNeed(),
                    state.rejections,
                )
                    ?.let { stop to it }
            }
        } ?: return
        prompts.recordPrompt(vehicle.id, now)
        notifier.post(vehicle, station, stop.startUtcMs)
    }
}

private fun Vehicle.fillNeed(): FillNeed = when {
    isElectric -> FillNeed.Electric
    tanks.any { it.energyType == EnergyType.CNG } -> FillNeed.Cng
    else -> FillNeed.Liquid
}

/** Posts the fill-up prompt. */
interface FillUpNotifier {
    fun post(vehicle: Vehicle, station: Station, atUtcMs: Long)
}

/** "Filled up at HP · Indiranagar? Tap to log." with "Log it" and "Not a fill-up" (design.md screen 31). */
internal class NotificationFillUpNotifier @Inject constructor(@param:ApplicationContext private val context: Context) :
    FillUpNotifier {
    // POST_NOTIFICATIONS is checked just below; lint doesn't follow the check.
    @SuppressLint("MissingPermission")
    override fun post(vehicle: Vehicle, station: Station, atUtcMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val label = station.label ?: context.getString(R.string.fillup_station_unnamed)
        val id = notificationId(vehicle.id)
        val open = PendingIntent.getActivity(
            context,
            id,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.putExtra(NotificationRoutes.EXTRA_OPEN, NotificationRoutes.OPEN_ADD_FUEL_PROMPT)
                ?.putExtra(
                    NotificationRoutes.EXTRA_ARG,
                    FillUpPrefill(vehicle.id, atUtcMs, station.label, station.lat, station.lng).encode(),
                )
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notMine = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, FillUpActionReceiver::class.java)
                .setAction(FillUpActionReceiver.ACTION_NOT_A_FILL_UP)
                .putExtra(FillUpActionReceiver.EXTRA_STATION, station.osmId)
                .putExtra(FillUpActionReceiver.EXTRA_NOTIFICATION, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.FILL_UP_PROMPTS)
            .setSmallIcon(FwIcons.Fuel)
            .setContentTitle(context.getString(R.string.fillup_title, label))
            .setContentText(context.getString(R.string.fillup_body, vehicle.name))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.fillup_log), open)
            .addAction(0, context.getString(R.string.fillup_not_one), notMine)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Revoked between the check and the post.
        }
    }

    // One per vehicle: a newer prompt replaces an older one.
    private fun notificationId(vehicleId: String) = NOTIFICATION_BASE + (vehicleId.hashCode() and ID_MASK)

    private companion object {
        const val NOTIFICATION_BASE = 7_300
        const val ID_MASK = 0xFFF
    }
}

/** "Not a fill-up": counted for the station (three and it's never prompted again), and the notification goes. */
@AndroidEntryPoint
class FillUpActionReceiver : BroadcastReceiver() {
    @Inject lateinit var prompts: FillUpPromptRepository

    @Inject
    @Dispatcher(FwDispatcher.IO)
    lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NOT_A_FILL_UP) return
        val station = intent.getLongExtra(EXTRA_STATION, 0L)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        if (station == 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + io).launch {
            try {
                prompts.recordRejection(station)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION_NOT_A_FILL_UP = "app.fuelwise.fillup.NOT_A_FILL_UP"
        const val EXTRA_STATION = "station"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

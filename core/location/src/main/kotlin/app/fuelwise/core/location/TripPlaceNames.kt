package app.fuelwise.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import app.fuelwise.core.data.repository.LocationPrivacyRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.trip.AddressParts
import app.fuelwise.core.domain.trip.PrivatePlace
import app.fuelwise.core.domain.trip.placeLabel
import app.fuelwise.core.domain.trip.privatePlaceFor
import app.fuelwise.core.domain.trip.roundForLookup
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Trip start and end names (owner decision 12 Nov 2026, user feedback). The one documented exception to CLAUDE.md
// rule 5: a recorded trip's start and end, rounded to about 110 m, go to Google through Android Geocoder. Never the
// route, never a point inside a private place, and only while the "Place names" setting is on (default on).

/** Names recorded trips' ends in the background; safe to call often, it does nothing when there's nothing to name. */
interface TripPlaceNames {
    /** Names recent trips (the last [RECENT_DAYS] days), or every trip with a saved route when [all]. */
    fun request(all: Boolean = false)

    companion object {
        const val RECENT_DAYS = 7L
    }
}

/** A reverse lookup of one point; null when there's no answer (offline, no service, nothing there). */
interface PlaceLookup {
    suspend fun lookup(lat: Double, lng: Double): AddressParts?
}

@Singleton
internal class RouteTripPlaceNames @Inject constructor(
    private val trips: TripRepository,
    private val privacy: LocationPrivacyRepository,
    private val lookup: PlaceLookup,
    private val clock: Clock,
    @param:RecorderScope private val scope: CoroutineScope,
) : TripPlaceNames {
    private val lock = Mutex()

    override fun request(all: Boolean) {
        scope.launch { nameMissing(all) }
    }

    /** Fills in only the ends that are still empty; a name the user typed is never replaced. */
    suspend fun nameMissing(all: Boolean) = lock.withLock {
        if (!privacy.placeNames.first()) return@withLock
        val since = if (all) Long.MIN_VALUE else clock.millis() - TripPlaceNames.RECENT_DAYS * MS_PER_DAY
        val places = privacy.privatePlaces.first()
        trips.observeTrips().first()
            .filter { it.needsNames() && (it.endUtcMs ?: it.startUtcMs) >= since }
            .forEach { trip ->
                val route = trips.route(trip.id)
                if (route.isEmpty()) return@forEach
                val from = trip.fromPlace ?: name(route.first(), places)
                val to = trip.toPlace ?: name(route.last(), places)
                if (from == trip.fromPlace && to == trip.toPlace) return@forEach
                // Re-read: the user may have edited the trip while we were looking.
                val latest = trips.observeTrip(trip.id).first() ?: return@forEach
                trips.save(latest.copy(fromPlace = latest.fromPlace ?: from, toPlace = latest.toPlace ?: to))
            }
    }

    private suspend fun name(point: TrackPoint, places: List<PrivatePlace>): String? =
        privatePlaceFor(point.lat, point.lng, places)?.name
            ?: lookup.lookup(roundForLookup(point.lat), roundForLookup(point.lng))?.let(::placeLabel)

    private fun Trip.needsNames() = source != TripSource.Manual && status == TripStatus.Done &&
        (fromPlace == null || toPlace == null)

    private companion object {
        const val MS_PER_DAY = 24L * 60 * 60 * 1_000
    }
}

/** Android Geocoder, in the app's language. Off-device on most phones (Google), hence the setting and the rounding. */
internal class GeocoderPlaceLookup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : PlaceLookup {
    override suspend fun lookup(lat: Double, lng: Double): AddressParts? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, context.resources.configuration.locales[0])
        val address = withTimeoutOrNull(TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        lat,
                        lng,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) =
                                cont.resume(addresses.firstOrNull())

                            override fun onError(errorMessage: String?) = cont.resume(null)
                        },
                    )
                }
            } else {
                withContext(io) { blockingLookup(geocoder, lat, lng) }
            }
        } ?: return null
        return AddressParts(address.subLocality, address.locality, address.subAdminArea, address.featureName)
    }

    @Suppress("DEPRECATION") // The listener form needs Android 13; below it, this runs off the main thread.
    private fun blockingLookup(geocoder: Geocoder, lat: Double, lng: Double): Address? = try {
        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
    } catch (_: IOException) {
        // Offline or the service is down: the trip is tried again later.
        null
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

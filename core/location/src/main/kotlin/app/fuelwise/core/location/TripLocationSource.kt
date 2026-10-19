package app.fuelwise.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import app.fuelwise.core.model.TrackPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** GPS fixes for a recording trip. Collecting the flow holds one location request; cancelling removes it. */
interface TripLocationSource {
    /** True when precise while-in-use location is granted. */
    val hasPermission: Boolean

    /** Batches of fixes (segment 0) for at most [lifetimeMs]; every request has a lifetime (CLAUDE.md rule 11). */
    fun fixes(lifetimeMs: Long): Flow<List<TrackPoint>>

    /** Delivers any fixes held in the batch now: used while the live screen is open. */
    fun flush()
}

/**
 * Fused Location Provider, sampled as CLAUDE.md rule 11 and PRD F-21.3 set: high accuracy, about every 5 s or 25 m,
 * delivered in batches of up to [MAX_BATCH_DELAY_MS] so the CPU rarely wakes. Don't turn this into a fixed
 * 1-minute interval: it breaks route accuracy.
 */
internal class FusedTripLocationSource @Inject constructor(@param:ApplicationContext private val context: Context) :
    TripLocationSource {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun fixes(lifetimeMs: Long): Flow<List<TrackPoint>> = callbackFlow {
        if (!hasPermission) {
            close(SecurityException("Precise location not granted"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, SAMPLE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
            .setMaxUpdateDelayMillis(MAX_BATCH_DELAY_MS)
            .setDurationMillis(lifetimeMs)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                trySend(result.locations.map { it.toPoint() })
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Revoked between the check and the request.
            close(e)
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    override fun flush() {
        client.flushLocations()
    }

    private fun Location.toPoint() = TrackPoint(
        atUtcMs = time,
        lat = latitude,
        lng = longitude,
        accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        speedMps = if (hasSpeed()) speed else null,
        segment = 0,
    )

    private companion object {
        const val SAMPLE_INTERVAL_MS = 5_000L
        const val MIN_UPDATE_DISTANCE_M = 25f
        const val MAX_BATCH_DELAY_MS = 90_000L
    }
}

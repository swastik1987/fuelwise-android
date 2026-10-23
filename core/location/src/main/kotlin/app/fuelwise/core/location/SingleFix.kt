package app.fuelwise.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** One position, for marking a private place (PRD F-21.6). Never sent anywhere (CLAUDE.md rule 5). */
data class Fix(val lat: Double, val lng: Double, val accuracyM: Float)

/** A single current location, while the app is on screen. */
interface SingleFix {
    val hasPermission: Boolean

    /** Null without permission, or when no fix arrives in time. */
    suspend fun once(): Fix?
}

/**
 * CLAUDE.md rule 11 single fix: balanced priority, 10 s timeout, a cached fix up to 2 minutes old is fine. The
 * request ends by itself; cancelling the caller cancels it too.
 */
internal class FusedSingleFix @Inject constructor(@param:ApplicationContext private val context: Context) : SingleFix {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override val hasPermission: Boolean
        get() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    // Checked through hasPermission just below; lint doesn't follow it.
    @SuppressLint("MissingPermission")
    override suspend fun once(): Fix? {
        if (!hasPermission) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setDurationMillis(TIMEOUT_MS)
            .setMaxUpdateAgeMillis(MAX_AGE_MS)
            .build()
        val cancel = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cancel.cancel() }
            try {
                client.getCurrentLocation(request, cancel.token)
                    .addOnSuccessListener { location ->
                        cont.resume(location?.let { Fix(it.latitude, it.longitude, it.accuracy) })
                    }
                    .addOnFailureListener { cont.resume(null) }
                    .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MAX_AGE_MS = 2 * 60 * 1_000L
    }
}

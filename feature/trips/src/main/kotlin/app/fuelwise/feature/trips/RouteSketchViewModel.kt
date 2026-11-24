package app.fuelwise.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.trip.RouteSketch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Route thumbnails for the Trips cards (owner choice 24 Nov 2026, option B). Each saved route is decrypted and reduced
 * to a [RouteSketch] once, off the main thread, the first time its card appears, and kept while the screen lives.
 * Nothing leaves the phone.
 */
@HiltViewModel
class RouteSketchViewModel @Inject constructor(
    private val trips: TripRepository,
    @param:Dispatcher(FwDispatcher.Default) private val cpu: CoroutineDispatcher,
) : ViewModel() {
    private val cache = MutableStateFlow<Map<String, RouteSketch?>>(emptyMap())

    /** Drawn sketches by trip ID; a null value means the trip has no route to draw. */
    val sketches: StateFlow<Map<String, RouteSketch?>> = cache
    private val requested = mutableSetOf<String>()

    fun load(tripId: String) {
        if (tripId in cache.value || !requested.add(tripId)) return
        viewModelScope.launch {
            val sketch = withContext(cpu) { RouteSketch.of(trips.route(tripId)) }
            cache.update { it + (tripId to sketch) }
        }
    }
}

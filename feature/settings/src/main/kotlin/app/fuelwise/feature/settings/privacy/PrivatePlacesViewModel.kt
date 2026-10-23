package app.fuelwise.feature.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.LocationPrivacyRepository
import app.fuelwise.core.domain.trip.PrivatePlace
import app.fuelwise.core.location.SingleFix
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrivatePlacesUiState(
    val loading: Boolean = true,
    val places: List<PrivatePlace> = emptyList(),
    /** Waiting for the single location fix. */
    val locating: Boolean = false,
)

sealed interface PrivatePlacesEvent {
    data class Added(val name: String) : PrivatePlacesEvent

    /** No fix within 10 s, or location is off. */
    data object NoFix : PrivatePlacesEvent
}

/**
 * Private places (PRD F-21.6, design.md screen 29 "Location & trips"): spots trimmed from route ends. A place is the
 * phone's current position, taken once; it stays on the phone, encrypted, and is never looked up anywhere.
 */
@HiltViewModel
class PrivatePlacesViewModel @Inject constructor(
    private val privacy: LocationPrivacyRepository,
    private val fix: SingleFix,
) : ViewModel() {
    private val locating = MutableStateFlow(false)

    val state: StateFlow<PrivatePlacesUiState> = combine(privacy.privatePlaces, locating) { places, busy ->
        PrivatePlacesUiState(loading = false, places = places, locating = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PrivatePlacesUiState())

    private val eventChannel = Channel<PrivatePlacesEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /** Marks where the phone is now as [name]. The screen has asked for location first. */
    fun addHere(name: String) {
        if (locating.value) return
        locating.value = true
        viewModelScope.launch {
            try {
                val here = fix.once()
                if (here == null) {
                    eventChannel.send(PrivatePlacesEvent.NoFix)
                } else {
                    privacy.addPrivatePlace(name, here.lat, here.lng)
                    eventChannel.send(PrivatePlacesEvent.Added(name))
                }
            } finally {
                locating.value = false
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch { privacy.removePrivatePlace(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

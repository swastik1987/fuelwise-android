package app.fuelwise.feature.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.trip.EstimateTripCost
import app.fuelwise.core.domain.trip.TripCostBasis
import app.fuelwise.core.domain.trip.TripSplit
import app.fuelwise.core.domain.trip.UpiPayLink
import app.fuelwise.core.domain.trip.splitTripCost
import app.fuelwise.core.domain.trip.tripCost
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A toll, parking or other cost linked to the trip. */
data class LinkedCost(val expense: Expense, val category: Category?)

data class TripDetailUiState(
    val loading: Boolean = true,
    /** Null once loaded means the trip was deleted. */
    val trip: Trip? = null,
    val vehicle: Vehicle? = null,
    val linked: List<LinkedCost> = emptyList(),
    /** How the fuel estimate was worked out, when today's figures still give the saved amount. */
    val fuelBasis: TripCostBasis? = null,
    val split: TripSplit? = null,
    val upiVpa: String? = null,
    val upiName: String? = null,
    val upiError: Boolean = false,
    /** The recorded route of a GPS trip; empty for manual trips or a restore without encrypted backups. */
    val route: List<TrackPoint> = emptyList(),
)

sealed interface TripDetailEvent {
    /** Share [link] (null when the user has no UPI ID) with the split. */
    data class Share(val split: TripSplit, val link: String?) : TripDetailEvent

    /** Ask for the user's UPI ID before sharing. */
    data object NeedUpi : TripDetailEvent
}

/** Trip detail with cost split (design.md screen 19; PRD F-07.1, F-07.4). */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trips: TripRepository,
    vehicles: VehicleRepository,
    private val entries: EntryRepository,
    categories: CategoryRepository,
    private val userData: UserDataRepository,
    private val estimateCost: EstimateTripCost,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TripDetailRoute>()
    private val people = MutableStateFlow(DEFAULT_PEOPLE)
    private val upiError = MutableStateFlow(false)

    val state: StateFlow<TripDetailUiState> = combine(
        combine(trips.observeTrip(route.tripId), vehicles.observeVehicles(), entries.observeAllRefuels(), ::Triple),
        entries.observeAllExpenses(),
        categories.observeCategories(),
        userData.userData,
        combine(people, upiError, flow { emit(trips.route(route.tripId)) }, ::Triple),
    ) { (trip, vehicleList, refuels), expenses, cats, user, (count, badUpi, recorded) ->
        if (trip == null) return@combine TripDetailUiState(loading = false)
        val vehicle = vehicleList.firstOrNull { it.id == trip.vehicleId }
        val byId = cats.associateBy { it.id }
        val now = vehicle?.let { estimateCost(it, refuels, trip.distanceKm) }
        TripDetailUiState(
            loading = false,
            trip = trip,
            vehicle = vehicle,
            linked = expenses.filter { it.tripId == trip.id }.map { LinkedCost(it, byId[it.categoryId]) },
            fuelBasis = now?.takeIf { it.fuelCost == trip.estimatedFuelCost }?.basis,
            split = tripCost(trip, expenses)?.let { splitTripCost(it, count) },
            upiVpa = user.upiVpa,
            upiName = user.upiName,
            upiError = badUpi,
            route = recorded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripDetailUiState())

    private val eventChannel = Channel<TripDetailEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun onPeople(count: Int) = people.update { count.coerceIn(1, MAX_PEOPLE) }

    /** Shares the split; the UPI link goes with it once the user's UPI ID is known (PRD F-07.4). */
    fun share(note: String) {
        viewModelScope.launch { shareNow(note) }
    }

    /** Saves the UPI ID typed once, then shares; false (and an error shown) if it isn't a UPI ID. */
    fun saveUpiAndShare(vpa: String, name: String, note: String): Boolean {
        if (!UpiPayLink.isValidVpa(vpa)) {
            upiError.value = true
            return false
        }
        upiError.value = false
        viewModelScope.launch {
            userData.setUpiPayee(vpa, name)
            shareNow(note)
        }
        return true
    }

    private suspend fun shareNow(note: String) {
        val split = state.first { !it.loading }.split ?: return
        val user = userData.userData.first()
        // Alone, there's nobody to pay you: share the summary without a link.
        if (split.people == 1) {
            eventChannel.send(TripDetailEvent.Share(split, null))
            return
        }
        val vpa = user.upiVpa
        if (vpa == null) {
            eventChannel.send(TripDetailEvent.NeedUpi)
            return
        }
        val link = UpiPayLink.build(vpa, user.upiName.orEmpty(), split.share, note)
        eventChannel.send(TripDetailEvent.Share(split, link))
    }

    fun clearUpiError() {
        upiError.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_PEOPLE = 2
        const val MAX_PEOPLE = 20
    }
}

package app.fuelwise.feature.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FuelPriceRepository
import app.fuelwise.core.data.repository.StationRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.designsystem.component.FwPhoto
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.domain.prices.FairPrice
import app.fuelwise.core.domain.prices.priceFuelFor
import app.fuelwise.core.domain.station.StationMatch
import app.fuelwise.core.domain.station.StationOption
import app.fuelwise.core.domain.station.fillNeedFor
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.photo.ThumbnailCache
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EntryDetailUiState {
    data object Loading : EntryDetailUiState

    data object Gone : EntryDetailUiState

    data class FillUp(
        val refuel: Refuel,
        val vehicle: Vehicle?,
        val result: IntervalResult?,
        /** km since the previous fill of the same tank. */
        val distanceSincePrevious: Double?,
        /** "Did I pay fair?" against the home-city price that day (PRD F-09.3). */
        val fairPrice: FairPrice? = null,
        /** The vehicle's display units (PRD F-18) for the interval's efficiency. */
        val units: DisplayUnits = DisplayUnits.INDIA,
        val photos: List<FwPhoto> = emptyList(),
    ) : EntryDetailUiState

    data class Cost(
        val expense: Expense,
        val vehicle: Vehicle?,
        val category: Category?,
        val photos: List<FwPhoto> = emptyList(),
    ) : EntryDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val entries: EntryRepository,
    vehicles: VehicleRepository,
    categories: CategoryRepository,
    lookupIntervals: LookupIntervals,
    userData: UserDataRepository,
    private val cityPrices: FuelPriceRepository,
    private val documents: DocumentRepository,
    private val stations: StationRepository,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<EntryDetailRoute>()
    private val source = EntrySource.valueOf(route.source)

    private val thumbnails = ThumbnailCache()

    /** The entry's photos (PRD F-02.1, F-04.1), including a pump-meter scan (F-15). */
    private val photos = documents.observeAttachments(route.id).map { all ->
        withContext(io) {
            thumbnails.of(all.filter { it.mimeType.startsWith("image/") }, { it.id }, documents::readAttachment)
        }
    }.onStart { emit(emptyList()) }

    val uiState: StateFlow<EntryDetailUiState> = when (source) {
        EntrySource.Refuel -> entries.observeRefuel(route.id).flatMapLatest { refuel ->
            if (refuel == null) {
                flowOf(EntryDetailUiState.Gone)
            } else {
                combine(
                    entries.observeRefuels(refuel.vehicleId),
                    vehicles.observeVehicle(refuel.vehicleId),
                    userData.userData,
                ) { all, vehicle, data ->
                    val tankFills = all.filter { it.tankId == refuel.tankId }
                    val previous = tankFills.filter { it.odometerKm < refuel.odometerKm }.maxByOrNull { it.odometerKm }
                    EntryDetailUiState.FillUp(
                        refuel = refuel,
                        vehicle = vehicle,
                        result = lookupIntervals(tankFills)[refuel.id],
                        distanceSincePrevious = previous?.let { refuel.odometerKm - it.odometerKm },
                        fairPrice = fairPrice(refuel, vehicle, data.homeCityId),
                        units = data.units.forVehicle(refuel.vehicleId),
                    )
                }
            }
        }

        EntrySource.Expense -> entries.observeExpense(route.id).flatMapLatest { expense ->
            if (expense == null) {
                flowOf(EntryDetailUiState.Gone)
            } else {
                combine(vehicles.observeVehicle(expense.vehicleId), categories.observeCategories()) { vehicle, cats ->
                    EntryDetailUiState.Cost(expense, vehicle, cats.firstOrNull { it.id == expense.categoryId })
                }
            }
        }
    }.combine(photos) { s, p ->
        when (s) {
            is EntryDetailUiState.FillUp -> s.copy(photos = p)
            is EntryDetailUiState.Cost -> s.copy(photos = p)
            else -> s
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryDetailUiState.Loading)

    private suspend fun fairPrice(refuel: Refuel, vehicle: Vehicle?, homeCityId: String?): FairPrice? {
        val tank = vehicle?.tanks?.firstOrNull { it.id == refuel.tankId } ?: return null
        val fuel = priceFuelFor(tank.energyType, refuel.grade) ?: return null
        val day = Instant.ofEpochMilli(refuel.dateTimeUtcMs).atZone(ZoneId.of(refuel.zoneId)).toLocalDate()
        val history = cityPrices.history(homeCityId ?: return null, fuel, day.minusDays(FairPrice.MAX_AGE_DAYS), day)
        return FairPrice.of(refuel.unitPrice, day, history)
    }

    private val _stationEdit = MutableStateFlow<StationEdit?>(null)

    /** The "Change station" sheet, or null when it's closed. */
    val stationEdit: StateFlow<StationEdit?> = _stationEdit.asStateFlow()

    /**
     * Opens "Change station" (PRD F-02.1, F-21.1). With a saved position it lists the stations near it, from the
     * bundled list and the user's other fill-ups, matched on the phone. Without one, the user can only type a name.
     */
    fun onEditStation() {
        val fill = uiState.value as? EntryDetailUiState.FillUp ?: return
        val refuel = fill.refuel
        val lat = refuel.lat
        val lng = refuel.lng
        val located = lat != null && lng != null
        _stationEdit.value = StationEdit(loading = located, hasLocation = located, current = refuel.stationName)
        if (lat == null || lng == null) return
        viewModelScope.launch {
            val listed = stations.near(lat, lng, StationMatch.WIDE_M)
            // This fill-up's own name doesn't vouch for itself.
            val history = entries.observeAllRefuels().first().filter { it.id != refuel.id }
            val energy = fill.vehicle?.tanks?.firstOrNull { it.id == refuel.tankId }?.energyType
            val options = StationMatch.options(lat, lng, listed, history, fillNeedFor(energy))
            _stationEdit.update { it?.copy(loading = false, options = options) }
        }
    }

    fun onDismissStation() {
        _stationEdit.value = null
    }

    /** A listed station: its name, and its position, which is more exact than where the phone was. */
    fun onPickStation(option: StationOption) = saveStation {
        it.copy(stationName = option.label, lat = option.lat, lng = option.lng)
    }

    /**
     * A typed name, kept at the saved position: the next fill-up near here suggests it first (PRD F-21.1 learning).
     * Blank clears the name.
     */
    fun onTypeStation(name: String) = saveStation {
        it.copy(stationName = name.trim().take(MAX_STATION).ifEmpty { null })
    }

    /** Drops the saved position; the name stays. */
    fun onRemoveLocation() = saveStation { it.copy(lat = null, lng = null) }

    private fun saveStation(change: (Refuel) -> Refuel) {
        val refuel = (uiState.value as? EntryDetailUiState.FillUp)?.refuel ?: return
        _stationEdit.value = null
        viewModelScope.launch { entries.saveRefuel(change(refuel)) }
    }

    private val deletedChannel = Channel<Unit>(Channel.BUFFERED)
    val deleted = deletedChannel.receiveAsFlow()

    fun delete() {
        viewModelScope.launch {
            when (source) {
                EntrySource.Refuel -> entries.deleteRefuel(route.id)
                EntrySource.Expense -> entries.deleteExpense(route.id)
            }
            deletedChannel.send(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_STATION = 60
    }
}

/** "Change station" (PRD F-02.1): nearby stations around the fill-up's saved position, best first. */
data class StationEdit(
    val loading: Boolean,
    /** The fill-up has a position: nearby stations can be listed, and the position removed. */
    val hasLocation: Boolean,
    val current: String?,
    val options: List<StationOption> = emptyList(),
)

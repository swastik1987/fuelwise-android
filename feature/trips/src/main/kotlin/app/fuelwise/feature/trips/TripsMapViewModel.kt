package app.fuelwise.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.trip.HeatPoint
import app.fuelwise.core.domain.trip.VisitedPlace
import app.fuelwise.core.domain.trip.capHeat
import app.fuelwise.core.domain.trip.densifyRoute
import app.fuelwise.core.domain.trip.frequentPlaces
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** How far back the map looks. */
enum class MapPeriod(val months: Long?) { ThreeMonths(QUARTER_MONTHS), Year(YEAR_MONTHS), All(null) }

private const val QUARTER_MONTHS = 3L
private const val YEAR_MONTHS = 12L

/** A fill-up with a place (logged from a fill-up prompt, PRD F-21.4), coloured by its energy. */
data class FillUpPin(val id: String, val lat: Double, val lng: Double, val energy: EnergyType?)

data class TripsMapUiState(
    val loading: Boolean = true,
    val period: MapPeriod = MapPeriod.Year,
    val heat: List<HeatPoint> = emptyList(),
    /** How many recorded trips the heat comes from. */
    val routes: Int = 0,
    val places: List<VisitedPlace> = emptyList(),
    val fillUps: List<FillUpPin> = emptyList(),
) {
    val empty: Boolean get() = heat.isEmpty() && places.isEmpty() && fillUps.isEmpty()
}

/**
 * The consolidated trips map (owner decision 12 Nov 2026, user feedback; PRD F-07.7, free in R1): every saved route in
 * the period as a heatmap, places trips often start or end at, and fill-ups that have a place. Everything stays on the
 * phone; routes are decrypted here only to draw them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripsMapViewModel @Inject constructor(
    private val trips: TripRepository,
    entries: EntryRepository,
    vehicles: VehicleRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    @Dispatcher(FwDispatcher.Default) cpu: CoroutineDispatcher,
) : ViewModel() {
    private val period = MutableStateFlow(MapPeriod.Year)

    val state: StateFlow<TripsMapUiState> = combine(
        period,
        trips.observeTrips(),
        entries.observeAllRefuels(),
        vehicles.observeVehicles(includeArchived = true),
    ) { p, all, refuels, list -> Inputs(p, all, refuels, list) }
        .mapLatest { inputs ->
            val (p, all, refuels) = inputs
            val list = inputs.vehicles
            val since = p.months?.let { clock.instant().atZone(zoneId).minusMonths(it).toInstant().toEpochMilli() }
            val recorded = all.filter {
                it.source != TripSource.Manual && it.status == TripStatus.Done &&
                    (since == null || it.startUtcMs >= since)
            }
            val routes = recorded.map { trips.route(it.id) }.filter { it.isNotEmpty() }
            val ends = routes.flatMap { r -> listOf(r.first(), r.last()).map { HeatPoint(it.lat, it.lng) } }
            val energies = list.flatMap { v -> v.tanks.map { it.id to it.energyType } }.toMap()
            TripsMapUiState(
                loading = false,
                period = p,
                heat = capHeat(routes.flatMap { densifyRoute(it) }),
                routes = routes.size,
                places = frequentPlaces(ends),
                fillUps = refuels.mapNotNull { r ->
                    val lat = r.lat ?: return@mapNotNull null
                    val lng = r.lng ?: return@mapNotNull null
                    if (since != null && r.dateTimeUtcMs < since) return@mapNotNull null
                    FillUpPin(r.id, lat, lng, energies[r.tankId])
                },
            )
        }
        .flowOn(cpu)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripsMapUiState())

    fun selectPeriod(p: MapPeriod) {
        period.value = p
    }

    private data class Inputs(
        val period: MapPeriod,
        val trips: List<Trip>,
        val refuels: List<Refuel>,
        val vehicles: List<Vehicle>,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

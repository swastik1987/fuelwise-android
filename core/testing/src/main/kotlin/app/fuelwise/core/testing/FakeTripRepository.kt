package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.DeletedTrip
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory trips, newest first, with raw points and routes kept in plain memory. */
class FakeTripRepository(initial: List<Trip> = emptyList()) : TripRepository {
    private val state = MutableStateFlow(initial)
    private val deleted = mutableMapOf<String, Trip>()
    private val rawPoints = mutableMapOf<String, MutableList<TrackPoint>>()
    private val routes = mutableMapOf<String, List<TrackPoint>>()
    private var nextId = 1

    val trips: List<Trip> get() = state.value

    override fun observeTrips(): Flow<List<Trip>> = state.map { all -> all.sortedByDescending { it.startUtcMs } }

    override fun observeTrip(id: String): Flow<Trip?> = state.map { all -> all.firstOrNull { it.id == id } }

    override fun observeUnfinished(): Flow<List<Trip>> =
        state.map { all -> all.filter { it.status != TripStatus.Done } }

    override suspend fun save(trip: Trip): String {
        val id = trip.id.ifBlank { "trip-${nextId++}" }
        state.update { all -> all.filterNot { it.id == id } + trip.copy(id = id) }
        return id
    }

    override suspend fun delete(id: String): DeletedTrip {
        state.value.firstOrNull { it.id == id }?.let { deleted[id] = it }
        state.update { all -> all.filterNot { it.id == id } }
        return DeletedTrip(id, 0L)
    }

    override suspend fun undoDelete(token: DeletedTrip) {
        deleted.remove(token.id)?.let { trip -> state.update { it + trip } }
    }

    override suspend fun appendPoints(tripId: String, points: List<TrackPoint>) {
        rawPoints.getOrPut(tripId) { mutableListOf() } += points
    }

    override suspend fun points(tripId: String): List<TrackPoint> = rawPoints[tripId].orEmpty().toList()

    override suspend fun finishRecording(trip: Trip, route: List<TrackPoint>) {
        save(trip)
        routes[trip.id] = route
        rawPoints.remove(trip.id)
    }

    override suspend fun discardRecording(tripId: String) {
        state.update { all -> all.filterNot { it.id == tripId } }
        rawPoints.remove(tripId)
    }

    override suspend fun route(tripId: String): List<TrackPoint> = routes[tripId].orEmpty()

    override suspend fun dropRoutes(endedBeforeUtcMs: Long?): Int {
        val ends = state.value.associate { it.id to (it.endUtcMs ?: it.startUtcMs) }
        val gone = routes.keys.filter { id -> endedBeforeUtcMs == null || (ends[id] ?: 0) < endedBeforeUtcMs }
        gone.forEach(routes::remove)
        return gone.size
    }
}

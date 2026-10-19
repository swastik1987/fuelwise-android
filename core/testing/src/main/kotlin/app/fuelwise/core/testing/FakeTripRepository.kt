package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.DeletedTrip
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory trips, newest first. */
class FakeTripRepository(initial: List<Trip> = emptyList()) : TripRepository {
    private val state = MutableStateFlow(initial)
    private val deleted = mutableMapOf<String, Trip>()
    private var nextId = 1

    val trips: List<Trip> get() = state.value

    override fun observeTrips(): Flow<List<Trip>> = state.map { all -> all.sortedByDescending { it.startUtcMs } }

    override fun observeTrip(id: String): Flow<Trip?> = state.map { all -> all.firstOrNull { it.id == id } }

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
}

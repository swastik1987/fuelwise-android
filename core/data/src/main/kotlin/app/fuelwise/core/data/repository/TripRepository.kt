package app.fuelwise.core.data.repository

import app.fuelwise.core.data.mapper.toEntity
import app.fuelwise.core.data.mapper.toModel
import app.fuelwise.core.database.dao.TripDao
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.model.Trip
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Trips (PRD F-07). Deletes are tombstones with undo. */
interface TripRepository {
    /** Newest first. */
    fun observeTrips(): Flow<List<Trip>>

    fun observeTrip(id: String): Flow<Trip?>

    /** Inserts or replaces. A blank [Trip.id] means "new": an id is assigned and returned. */
    suspend fun save(trip: Trip): String

    suspend fun delete(id: String): DeletedTrip

    suspend fun undoDelete(token: DeletedTrip)
}

data class DeletedTrip(val id: String, val deletedAtUtcMs: Long)

internal class OfflineTripRepository @Inject constructor(
    private val dao: TripDao,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
) : TripRepository {
    override fun observeTrips(): Flow<List<Trip>> = dao.observeTrips().map { rows -> rows.map { it.toModel() } }

    override fun observeTrip(id: String): Flow<Trip?> = dao.observeTrip(id).map { it?.toModel() }

    override suspend fun save(trip: Trip): String {
        val id = trip.id.ifBlank { ids.newId() }
        val existing = if (trip.id.isBlank()) null else dao.trip(trip.id)
        // The model doesn't carry the route; an edit keeps the one a GPS trip recorded.
        dao.upsert(trip.copy(id = id).toEntity(sync.stamp(existing?.sync?.createdAt), existing?.polylineAttachmentId))
        return id
    }

    override suspend fun delete(id: String): DeletedTrip {
        val at = sync.now()
        dao.tombstone(id, at, sync.device())
        return DeletedTrip(id, at)
    }

    override suspend fun undoDelete(token: DeletedTrip) =
        dao.restore(token.id, token.deletedAtUtcMs, sync.now(), sync.device())
}

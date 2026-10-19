package app.fuelwise.core.data.repository

import app.fuelwise.core.data.mapper.toEntity
import app.fuelwise.core.data.mapper.toModel
import app.fuelwise.core.data.vault.AttachmentStore
import app.fuelwise.core.data.vault.VaultCipher
import app.fuelwise.core.database.dao.DocumentDao
import app.fuelwise.core.database.dao.TripDao
import app.fuelwise.core.database.model.TripPointBatchEntity
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.domain.trip.TrackCodec
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Trips (PRD F-07). Deletes are tombstones with undo. */
interface TripRepository {
    /** Newest first. */
    fun observeTrips(): Flow<List<Trip>>

    fun observeTrip(id: String): Flow<Trip?>

    /** GPS trips not finished yet: recording, paused, or cut short by a crash or reboot (PRD F-07.3). */
    fun observeUnfinished(): Flow<List<Trip>>

    /** Inserts or replaces. A blank [Trip.id] means "new": an id is assigned and returned. */
    suspend fun save(trip: Trip): String

    suspend fun delete(id: String): DeletedTrip

    suspend fun undoDelete(token: DeletedTrip)

    /** Appends raw fixes to a recording trip, encrypted at rest (CLAUDE.md rule 5). */
    suspend fun appendPoints(tripId: String, points: List<TrackPoint>)

    /** Every raw fix recorded so far, in recording order. */
    suspend fun points(tripId: String): List<TrackPoint>

    /**
     * Saves the finished trip with its simplified [route] as an encrypted attachment, then deletes the raw points:
     * from here on only the route is kept (privacy default agreed for M8).
     */
    suspend fun finishRecording(trip: Trip, route: List<TrackPoint>)

    /** Drops an unfinished trip and its raw points (too short to keep, or the user discarded it). */
    suspend fun discardRecording(tripId: String)

    /** The saved route of a GPS trip, or empty (manual trip, or a restore without encrypted backups). */
    suspend fun route(tripId: String): List<TrackPoint>
}

data class DeletedTrip(val id: String, val deletedAtUtcMs: Long)

internal class OfflineTripRepository @Inject constructor(
    private val dao: TripDao,
    private val attachments: DocumentDao,
    private val files: AttachmentStore,
    private val cipher: VaultCipher,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
    @param:Dispatcher(FwDispatcher.Default) private val cpu: CoroutineDispatcher,
) : TripRepository {
    override fun observeTrips(): Flow<List<Trip>> = dao.observeTrips().map { rows -> rows.map { it.toModel() } }

    override fun observeTrip(id: String): Flow<Trip?> = dao.observeTrip(id).map { it?.toModel() }

    override fun observeUnfinished(): Flow<List<Trip>> =
        dao.observeUnfinished().map { rows -> rows.map { it.toModel() } }

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

    override suspend fun appendPoints(tripId: String, points: List<TrackPoint>) {
        if (points.isEmpty()) return
        val seq = (dao.lastSeq(tripId) ?: -1) + 1
        val payload = withContext(cpu) { cipher.encrypt(TrackCodec.encode(points), batchAd(tripId, seq)) }
        dao.insertBatch(TripPointBatchEntity(ids.newId(), tripId, seq, payload, sync.now()))
    }

    override suspend fun points(tripId: String): List<TrackPoint> {
        val batches = dao.batches(tripId)
        return withContext(cpu) {
            batches.flatMap { TrackCodec.decode(cipher.decrypt(it.payload, batchAd(tripId, it.seq))) }
        }
    }

    override suspend fun finishRecording(trip: Trip, route: List<TrackPoint>) {
        val existing = dao.trip(trip.id)
        val routeId = if (route.isEmpty()) {
            existing?.polylineAttachmentId
        } else {
            val bytes = TrackCodec.encode(route)
            val attachmentId = ids.newId()
            val stored = files.write(attachmentId, bytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val size = bytes.size.toLong()
            val row = Attachment(attachmentId, trip.id, Attachment.OwnerType.Trip, stored, ROUTE_MIME, size, digest)
            attachments.upsertAttachment(row.toEntity(sync.stamp()))
            attachmentId
        }
        dao.upsert(trip.toEntity(sync.stamp(existing?.sync?.createdAt), routeId))
        dao.deleteBatches(trip.id)
    }

    override suspend fun discardRecording(tripId: String) {
        dao.tombstone(tripId, sync.now(), sync.device())
        dao.deleteBatches(tripId)
    }

    override suspend fun route(tripId: String): List<TrackPoint> {
        val attachmentId = dao.trip(tripId)?.polylineAttachmentId ?: return emptyList()
        val attachment = attachments.attachment(attachmentId) ?: return emptyList()
        return try {
            TrackCodec.decode(files.read(attachment.id, attachment.storedName))
        } catch (_: java.io.IOException) {
            // The file didn't come back with an unencrypted restore: the trip keeps its numbers, without a map.
            emptyList()
        }
    }

    // Binds a batch to its trip and position, so batches can't be swapped or reordered undetected.
    private fun batchAd(tripId: String, seq: Int) = "$tripId:$seq".toByteArray()

    private companion object {
        const val ROUTE_MIME = "application/vnd.fuelwise.track"
    }
}

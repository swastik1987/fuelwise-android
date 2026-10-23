package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import app.fuelwise.core.database.model.TripEntity
import app.fuelwise.core.database.model.TripPointBatchEntity
import kotlinx.coroutines.flow.Flow

/** Trips (PRD F-07). Deletes are tombstones (CLAUDE.md rule 3). */
@Dao
interface TripDao {
    @Query("SELECT * FROM trip WHERE deletedAt IS NULL ORDER BY startTime DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trip WHERE id = :id AND deletedAt IS NULL")
    fun observeTrip(id: String): Flow<TripEntity?>

    @Query("SELECT * FROM trip WHERE id = :id")
    suspend fun trip(id: String): TripEntity?

    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query(
        """
        UPDATE trip SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstone(id: String, at: Long, deviceId: String)

    /** Undo only clears the tombstone this delete set. */
    @Query(
        """
        UPDATE trip SET deletedAt = NULL, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt = :deletedAt
        """,
    )
    suspend fun restore(id: String, deletedAt: Long, at: Long, deviceId: String)

    /** Trips still marked recording or paused: the one in progress, or ones a crash or reboot cut short. */
    @Query("SELECT * FROM trip WHERE status IN ('Recording', 'Paused', 'Interrupted') AND deletedAt IS NULL")
    fun observeUnfinished(): Flow<List<TripEntity>>

    /**
     * Trips, deleted ones included, that still have a saved route and ended (or started, if never ended) before
     * [before]; every one with a route when [before] is null. For route retention and "Delete all location history".
     */
    @Query(
        """
        SELECT * FROM trip WHERE polylineAttachmentId IS NOT NULL
        AND (:before IS NULL OR COALESCE(endTime, startTime) < :before)
        """,
    )
    suspend fun tripsWithRoute(before: Long?): List<TripEntity>

    @Insert
    suspend fun insertBatch(batch: TripPointBatchEntity)

    @Query("SELECT * FROM trip_point_batch WHERE tripId = :tripId ORDER BY seq")
    suspend fun batches(tripId: String): List<TripPointBatchEntity>

    @Query("SELECT MAX(seq) FROM trip_point_batch WHERE tripId = :tripId")
    suspend fun lastSeq(tripId: String): Int?

    /** Raw points are transient (never synced): removed once the route is saved or the trip discarded. */
    @Query("DELETE FROM trip_point_batch WHERE tripId = :tripId")
    suspend fun deleteBatches(tripId: String)
}

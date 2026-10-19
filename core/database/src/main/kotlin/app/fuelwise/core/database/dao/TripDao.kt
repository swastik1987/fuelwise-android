package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.fuelwise.core.database.model.TripEntity
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
}

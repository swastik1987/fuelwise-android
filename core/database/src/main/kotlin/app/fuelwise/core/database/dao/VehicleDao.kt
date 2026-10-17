package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.fuelwise.core.database.model.TankEntity
import app.fuelwise.core.database.model.VehicleEntity
import app.fuelwise.core.database.model.VehicleWithTanks
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Transaction
    @Query(
        """
        SELECT * FROM vehicle
        WHERE deletedAt IS NULL AND (:includeArchived OR archived = 0)
        ORDER BY sortOrder, createdAt
        """,
    )
    fun observeVehicles(includeArchived: Boolean): Flow<List<VehicleWithTanks>>

    @Transaction
    @Query("SELECT * FROM vehicle WHERE id = :id AND deletedAt IS NULL")
    fun observeVehicle(id: String): Flow<VehicleWithTanks?>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM vehicle WHERE deletedAt IS NULL")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsertVehicle(vehicle: VehicleEntity)

    @Upsert
    suspend fun upsertTanks(tanks: List<TankEntity>)

    @Transaction
    suspend fun upsertVehicleWithTanks(vehicle: VehicleEntity, tanks: List<TankEntity>) {
        upsertVehicle(vehicle)
        upsertTanks(tanks)
    }

    @Query("UPDATE vehicle SET archived = :archived, updatedAt = :at, deviceId = :deviceId WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, at: Long, deviceId: String)

    /** Tombstones the vehicle and its live tanks with the same timestamp, so [restore] can bring back exactly those. */
    @Transaction
    suspend fun softDelete(id: String, at: Long, deviceId: String) {
        tombstoneVehicle(id, at, deviceId)
        tombstoneTanks(id, at, deviceId)
        tombstoneVehicleRefuels(id, at, deviceId)
        tombstoneVehicleExpenses(id, at, deviceId)
    }

    @Transaction
    suspend fun restore(id: String, deletedAt: Long, at: Long, deviceId: String) {
        restoreVehicle(id, deletedAt, at, deviceId)
        restoreTanks(id, deletedAt, at, deviceId)
        restoreVehicleRefuels(id, deletedAt, at, deviceId)
        restoreVehicleExpenses(id, deletedAt, at, deviceId)
    }

    @Query(
        """
        UPDATE refuel SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE vehicleId = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneVehicleRefuels(id: String, at: Long, deviceId: String)

    @Query(
        """
        UPDATE expense SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE vehicleId = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneVehicleExpenses(id: String, at: Long, deviceId: String)

    @Query(
        """
        UPDATE refuel SET deletedAt = NULL, updatedAt = :at, deviceId = :deviceId
        WHERE vehicleId = :id AND deletedAt = :deletedAt
        """,
    )
    suspend fun restoreVehicleRefuels(id: String, deletedAt: Long, at: Long, deviceId: String)

    @Query(
        """
        UPDATE expense SET deletedAt = NULL, updatedAt = :at, deviceId = :deviceId
        WHERE vehicleId = :id AND deletedAt = :deletedAt
        """,
    )
    suspend fun restoreVehicleExpenses(id: String, deletedAt: Long, at: Long, deviceId: String)

    @Query(
        """
        UPDATE vehicle SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneVehicle(id: String, at: Long, deviceId: String)

    @Query(
        """
        UPDATE tank SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE vehicleId = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstoneTanks(id: String, at: Long, deviceId: String)

    @Query(
        """
        UPDATE vehicle SET deletedAt = NULL, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt = :deletedAt
        """,
    )
    suspend fun restoreVehicle(id: String, deletedAt: Long, at: Long, deviceId: String)

    @Query(
        """
        UPDATE tank SET deletedAt = NULL, updatedAt = :at, deviceId = :deviceId
        WHERE vehicleId = :id AND deletedAt = :deletedAt
        """,
    )
    suspend fun restoreTanks(id: String, deletedAt: Long, at: Long, deviceId: String)
}

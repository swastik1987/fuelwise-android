package app.fuelwise.core.data.repository

import app.fuelwise.core.domain.vehicle.VehicleDraft
import app.fuelwise.core.model.Vehicle
import kotlinx.coroutines.flow.Flow

/** Single source of truth for vehicles is Room; reads are Flows, writes are suspend (CLAUDE.md). */
interface VehicleRepository {
    /** Live vehicles in garage order. Archived vehicles are hidden from the switcher (PRD F-01 AC). */
    fun observeVehicles(includeArchived: Boolean = false): Flow<List<Vehicle>>

    fun observeVehicle(id: String): Flow<Vehicle?>

    /** Creates the vehicle and its tanks from a validated draft. Returns the new vehicle id. */
    suspend fun add(draft: VehicleDraft): String

    suspend fun update(vehicle: Vehicle)

    suspend fun setArchived(id: String, archived: Boolean)

    /**
     * Tombstones the vehicle and its tanks (never a hard delete; CLAUDE.md rule 3). Keep the returned token for the
     * 10-second undo (PRD F-01 AC). Entries cascade the same way once they exist (M3).
     */
    suspend fun delete(id: String): DeletedVehicle

    suspend fun undoDelete(token: DeletedVehicle)
}

/** Undo token for [VehicleRepository.delete]. */
data class DeletedVehicle(val vehicleId: String, val deletedAtUtcMs: Long)

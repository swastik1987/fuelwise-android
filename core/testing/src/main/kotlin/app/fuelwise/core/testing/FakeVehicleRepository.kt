package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.DeletedVehicle
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.vehicle.VehicleDraft
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [VehicleRepository] for ViewModel and UI tests. Deleted vehicles are kept aside for undo. */
class FakeVehicleRepository(initial: List<Vehicle> = emptyList()) : VehicleRepository {
    private val live = MutableStateFlow(initial)
    private val deleted = mutableMapOf<String, Vehicle>()
    private var nextId = 1

    /** Drafts passed to [add], newest last, so tests can assert on what the UI sent. */
    val addedDrafts = mutableListOf<VehicleDraft>()

    override fun observeVehicles(includeArchived: Boolean): Flow<List<Vehicle>> =
        live.map { all -> all.filter { includeArchived || !it.isArchived }.sortedBy { it.sortOrder } }

    override fun observeVehicle(id: String): Flow<Vehicle?> = live.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun add(draft: VehicleDraft): String {
        addedDrafts += draft
        val id = "vehicle-${nextId++}"
        val vehicle = Vehicle(
            id = id,
            name = draft.name.trim(),
            type = draft.type,
            tanks = draft.fuelSetup.tanks.mapIndexed { i, e ->
                Tank(
                    "$id-tank-$i",
                    e,
                    draft.tankCapacities.getOrNull(i),
                    i == 0,
                    draft.defaultGrade.takeIf { e == EnergyType.PETROL },
                )
            },
            startOdometerKm = draft.odometerKm ?: 0.0,
            startOdometerAtUtcMs = 0,
            registrationNumber = draft.registrationNumber.ifBlank { null },
            emissionNorm = draft.emissionNorm,
            claimedKmPerUnit = draft.claimedKmPerUnit,
            make = draft.make.ifBlank { null },
            model = draft.model.ifBlank { null },
            variant = draft.variant.ifBlank { null },
            year = draft.year,
            purchaseDate = draft.purchaseDate,
            purchaseOdometerKm = draft.purchaseOdometerKm,
            purchasePrice = draft.purchasePrice,
            isHybrid = draft.isHybrid,
            custom = draft.custom,
            sortOrder = live.value.size,
        )
        live.update { it + vehicle }
        return id
    }

    override suspend fun update(vehicle: Vehicle) {
        live.update { all -> all.map { if (it.id == vehicle.id) vehicle else it } }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        live.update { all -> all.map { if (it.id == id) it.copy(isArchived = archived) else it } }
    }

    override suspend fun delete(id: String): DeletedVehicle {
        live.value.firstOrNull { it.id == id }?.let { deleted[id] = it }
        live.update { all -> all.filterNot { it.id == id } }
        return DeletedVehicle(id, deletedAtUtcMs = 0)
    }

    override suspend fun undoDelete(token: DeletedVehicle) {
        deleted.remove(token.vehicleId)?.let { vehicle -> live.update { it + vehicle } }
    }
}

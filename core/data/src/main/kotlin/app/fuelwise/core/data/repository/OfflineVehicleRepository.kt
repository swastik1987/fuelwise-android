package app.fuelwise.core.data.repository

import app.fuelwise.core.data.DeviceIdProvider
import app.fuelwise.core.data.mapper.toEntity
import app.fuelwise.core.data.mapper.toModel
import app.fuelwise.core.database.dao.VehicleDao
import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.database.model.TankEntity
import app.fuelwise.core.database.model.VehicleEntity
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.domain.vehicle.RegistrationNumber
import app.fuelwise.core.domain.vehicle.VehicleDraft
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Vehicle
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class OfflineVehicleRepository @Inject constructor(
    private val dao: VehicleDao,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val deviceIds: DeviceIdProvider,
    private val zoneId: ZoneId,
) : VehicleRepository {
    override fun observeVehicles(includeArchived: Boolean): Flow<List<Vehicle>> =
        dao.observeVehicles(includeArchived).map { rows -> rows.map { it.toModel() } }

    override fun observeVehicle(id: String): Flow<Vehicle?> = dao.observeVehicle(id).map { it?.toModel() }

    override suspend fun add(draft: VehicleDraft): String {
        val now = clock.millis()
        val sync = SyncMeta(createdAt = now, updatedAt = now, deviceId = deviceIds.deviceId())
        val vehicleId = ids.newId()
        val vehicle = VehicleEntity(
            id = vehicleId,
            name = draft.name.trim(),
            type = draft.type.name,
            make = draft.make.trim().ifEmpty { null },
            model = draft.model.trim().ifEmpty { null },
            variant = draft.variant.trim().ifEmpty { null },
            year = draft.year,
            registrationNumber = draft.registrationNumber.takeIf { it.isNotBlank() }?.let(RegistrationNumber::format),
            emissionNorm = draft.emissionNorm?.name,
            purchaseEpochDay = draft.purchaseDate?.toEpochDay(),
            purchaseOdometerKm = draft.purchaseOdometerKm,
            purchasePricePaise = draft.purchasePrice?.paise,
            claimedKmPerUnit = draft.claimedKmPerUnit,
            isHybrid = draft.isHybrid && draft.fuelSetup == FuelSetup.Petrol,
            customKmPerUnit = null,
            customPricePerUnitPaise = null,
            customRangeKm = null,
            archived = false,
            sortOrder = dao.maxSortOrder() + 1,
            startOdometerKm = requireNotNull(draft.odometerKm) { "Validate the draft before adding it" },
            startOdometerAt = now,
            startOdometerZoneId = zoneId.id,
            sync = sync,
        )
        val tanks = draft.fuelSetup.tanks.mapIndexed { index, energy ->
            TankEntity(
                id = ids.newId(),
                vehicleId = vehicleId,
                energyType = energy.name,
                capacity = draft.tankCapacities.getOrNull(index),
                isPrimary = index == 0,
                defaultGrade = draft.defaultGrade?.takeIf { energy == EnergyType.PETROL }?.name,
                sync = sync,
            )
        }
        dao.upsertVehicleWithTanks(vehicle, tanks)
        return vehicleId
    }

    override suspend fun update(vehicle: Vehicle) {
        val existing = dao.observeVehicle(vehicle.id).first() ?: return
        val now = clock.millis()
        val device = deviceIds.deviceId()
        val tankCreatedAt = existing.tanks.associate { it.id to it.sync.createdAt }
        dao.upsertVehicleWithTanks(
            vehicle.toEntity(existing.vehicle.sync.copy(updatedAt = now, deviceId = device), existing.vehicle),
            vehicle.tanks.map { tank ->
                tank.toEntity(
                    vehicleId = vehicle.id,
                    sync = SyncMeta(createdAt = tankCreatedAt[tank.id] ?: now, updatedAt = now, deviceId = device),
                )
            },
        )
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        dao.setArchived(id, archived, clock.millis(), deviceIds.deviceId())
    }

    override suspend fun delete(id: String): DeletedVehicle {
        val at = clock.millis()
        dao.softDelete(id, at, deviceIds.deviceId())
        return DeletedVehicle(id, at)
    }

    override suspend fun undoDelete(token: DeletedVehicle) {
        dao.restore(token.vehicleId, token.deletedAtUtcMs, clock.millis(), deviceIds.deviceId())
    }
}

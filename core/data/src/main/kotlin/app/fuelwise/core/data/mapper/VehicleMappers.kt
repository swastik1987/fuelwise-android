package app.fuelwise.core.data.mapper

import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.database.model.TankEntity
import app.fuelwise.core.database.model.VehicleEntity
import app.fuelwise.core.database.model.VehicleWithTanks
import app.fuelwise.core.model.CustomParameters
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import java.time.LocalDate

internal fun VehicleWithTanks.toModel(): Vehicle = Vehicle(
    id = vehicle.id,
    name = vehicle.name,
    type = enumValueOrNull<VehicleType>(vehicle.type) ?: VehicleType.Other,
    tanks = tanks.filter { it.sync.deletedAt == null }.sortedByDescending { it.isPrimary }.map { it.toModel() },
    startOdometerKm = vehicle.startOdometerKm,
    startOdometerAtUtcMs = vehicle.startOdometerAt,
    make = vehicle.make,
    model = vehicle.model,
    variant = vehicle.variant,
    year = vehicle.year,
    registrationNumber = vehicle.registrationNumber,
    emissionNorm = vehicle.emissionNorm?.let { enumValueOrNull<EmissionNorm>(it) },
    purchaseDate = vehicle.purchaseEpochDay?.let(LocalDate::ofEpochDay),
    purchaseOdometerKm = vehicle.purchaseOdometerKm,
    purchasePrice = vehicle.purchasePricePaise?.let(::Money),
    claimedKmPerUnit = vehicle.claimedKmPerUnit,
    isHybrid = vehicle.isHybrid,
    custom = CustomParameters(
        kmPerUnit = vehicle.customKmPerUnit,
        pricePerUnit = vehicle.customPricePerUnitPaise?.let(::Money),
        rangeKm = vehicle.customRangeKm,
    ),
    isArchived = vehicle.archived,
    sortOrder = vehicle.sortOrder,
)

internal fun TankEntity.toModel() = Tank(
    id = id,
    energyType = enumValueOrNull<EnergyType>(energyType) ?: EnergyType.PETROL,
    capacity = capacity,
    isPrimary = isPrimary,
    defaultGrade = defaultGrade?.let { enumValueOrNull<FuelGrade>(it) },
)

/** Fields the model doesn't carry (start-odometer zone) come from [existing]. */
internal fun Vehicle.toEntity(sync: SyncMeta, existing: VehicleEntity) = VehicleEntity(
    id = id,
    name = name.trim(),
    type = type.name,
    make = make,
    model = model,
    variant = variant,
    year = year,
    registrationNumber = registrationNumber,
    emissionNorm = emissionNorm?.name,
    purchaseEpochDay = purchaseDate?.toEpochDay(),
    purchaseOdometerKm = purchaseOdometerKm,
    purchasePricePaise = purchasePrice?.paise,
    claimedKmPerUnit = claimedKmPerUnit,
    isHybrid = isHybrid,
    customKmPerUnit = custom.kmPerUnit,
    customPricePerUnitPaise = custom.pricePerUnit?.paise,
    customRangeKm = custom.rangeKm,
    archived = isArchived,
    sortOrder = sortOrder,
    startOdometerKm = startOdometerKm,
    startOdometerAt = startOdometerAtUtcMs,
    startOdometerZoneId = existing.startOdometerZoneId,
    sync = sync,
)

internal fun Tank.toEntity(vehicleId: String, sync: SyncMeta) = TankEntity(
    id = id,
    vehicleId = vehicleId,
    energyType = energyType.name,
    capacity = capacity,
    isPrimary = isPrimary,
    defaultGrade = defaultGrade?.name,
    sync = sync,
)

/** Unknown names (from a newer app version's backup, say) map to null instead of crashing. */
internal inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? = enumValues<T>().firstOrNull {
    it.name ==
        name
}

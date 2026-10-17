package app.fuelwise.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * PRD §7 `vehicle`. Enums are stored by name; dates as epoch days; money as paise.
 * Photo and accent colour arrive with their own migration when the photo picker is built.
 */
@Entity(tableName = "vehicle")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val make: String?,
    val model: String?,
    val variant: String?,
    val year: Int?,
    val registrationNumber: String?,
    val emissionNorm: String?,
    val purchaseEpochDay: Long?,
    val purchaseOdometerKm: Double?,
    val purchasePricePaise: Long?,
    val claimedKmPerUnit: Double?,
    val isHybrid: Boolean,
    val customKmPerUnit: Double?,
    val customPricePerUnitPaise: Long?,
    val customRangeKm: Double?,
    val archived: Boolean,
    val sortOrder: Int,
    val startOdometerKm: Double,
    val startOdometerAt: Long,
    val startOdometerZoneId: String,
    @Embedded val sync: SyncMeta,
)

/** PRD §7 `tank`. [capacity] is in the energy type's canonical unit (L, kg, usable kWh). */
@Entity(tableName = "tank", indices = [Index("vehicleId")])
data class TankEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val energyType: String,
    val capacity: Double?,
    val isPrimary: Boolean,
    val defaultGrade: String?,
    @Embedded val sync: SyncMeta,
)

/** PRD §7 `category`. System presets have a fixed id and a [systemKey]; users can rename or hide them. */
@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val systemKey: String?,
    val customName: String?,
    val icon: String?,
    val color: Long?,
    val isHidden: Boolean,
    val includeInCostPerKm: Boolean,
    val sortOrder: Int,
    @Embedded val sync: SyncMeta,
)

/** A vehicle with all its tank rows, including tombstoned ones; filter `sync.deletedAt` when mapping. */
data class VehicleWithTanks(
    @Embedded val vehicle: VehicleEntity,
    @Relation(parentColumn = "id", entityColumn = "vehicleId")
    val tanks: List<TankEntity>,
)

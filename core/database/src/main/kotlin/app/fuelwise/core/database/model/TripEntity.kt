package app.fuelwise.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PRD §7 `trip`. Distance in km; [estCostPaise] is the fuel estimate fixed at save time. GPS routes (M8) are kept as
 * an encrypted attachment named by [polylineAttachmentId], never as rows here.
 */
@Entity(tableName = "trip", indices = [Index("vehicleId", "startTime")])
data class TripEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val startTime: Long,
    val endTime: Long?,
    val zoneId: String,
    val startOdo: Double?,
    val endOdo: Double?,
    val distanceKm: Double,
    val purpose: String,
    val customPurpose: String?,
    val fromPlace: String?,
    val toPlace: String?,
    val notes: String?,
    val polylineAttachmentId: String?,
    val estCostPaise: Long?,
    val source: String,
    val status: String,
    @Embedded val sync: SyncMeta,
)

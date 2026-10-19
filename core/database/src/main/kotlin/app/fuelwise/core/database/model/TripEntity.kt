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
    val recordedSeconds: Long? = null,
    val idleSeconds: Long? = null,
    val maxSpeedKmh: Double? = null,
    @Embedded val sync: SyncMeta,
)

/**
 * Raw GPS points of a trip being recorded, in encrypted batches (CLAUDE.md rule 5). Device-local and transient: the
 * rows are deleted once the trip is finished and its simplified route saved, so they carry no sync columns. Backed
 * up only with encrypted backups, like the documents vault.
 */
@Entity(tableName = "trip_point_batch", indices = [Index(value = ["tripId", "seq"], unique = true)])
data class TripPointBatchEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val seq: Int,
    /** AES-GCM ciphertext of the encoded points; the trip id and seq are its associated data. */
    val payload: ByteArray,
    val createdAt: Long,
) {
    // Room needs equals on entities with arrays; identity is the row id.
    override fun equals(other: Any?): Boolean = other is TripPointBatchEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

package app.fuelwise.sync.drive

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A backup file in Drive's app folder. */
data class RemoteBackup(val id: String, val name: String, val createdAtUtcMs: Long, val sizeBytes: Long) {
    val isEncrypted: Boolean get() = name.endsWith(ENCRYPTED_SUFFIX)

    val isSnapshot: Boolean get() = name.startsWith(SNAPSHOT_PREFIX)

    companion object {
        const val SNAPSHOT_PREFIX = "snapshot-"
        const val ENCRYPTED_SUFFIX = ".enc"

        fun snapshotName(createdAtUtcMs: Long, deviceId: String, encrypted: Boolean) =
            "$SNAPSHOT_PREFIX$createdAtUtcMs-$deviceId.json.gz" + if (encrypted) ENCRYPTED_SUFFIX else ""
    }
}

/**
 * Snapshots to delete (PRD F-14.4): keep the newest [KEEP_LATEST], plus the newest of each month for the last
 * [KEEP_MONTHS] months. Attachments are never pruned here.
 */
fun snapshotsToDelete(files: List<RemoteBackup>, nowUtcMs: Long, zone: ZoneId): List<RemoteBackup> {
    val snapshots = files.filter { it.isSnapshot }.sortedByDescending { it.createdAtUtcMs }
    val keep = snapshots.take(KEEP_LATEST).toMutableSet()
    val oldestMonth = YearMonth.from(Instant.ofEpochMilli(nowUtcMs).atZone(zone)).minusMonths(KEEP_MONTHS - 1L)
    snapshots.groupBy { YearMonth.from(Instant.ofEpochMilli(it.createdAtUtcMs).atZone(zone)) }
        .filterKeys { !it.isBefore(oldestMonth) }
        .values
        .forEach { month -> keep += month.first() }
    return snapshots.filterNot { it in keep }
}

const val KEEP_LATEST = 10
const val KEEP_MONTHS = 12

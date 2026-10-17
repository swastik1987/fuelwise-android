package app.fuelwise.core.database.model

/**
 * Columns every syncable row carries (CLAUDE.md rule 3). Rows are never hard-deleted: [deletedAt] is a tombstone,
 * so backups and (R2) multi-device sync can merge deletions with row-level last-writer-wins.
 */
data class SyncMeta(val createdAt: Long, val updatedAt: Long, val deviceId: String, val deletedAt: Long? = null)

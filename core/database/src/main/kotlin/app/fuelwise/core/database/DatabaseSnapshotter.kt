package app.fuelwise.core.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject

/** Every row of every table, tombstones included (PRD F-14.4: all entities, no derived data). */
data class DatabaseDump(val schemaVersion: Int, val tables: Map<String, List<Map<String, Any?>>>)

/**
 * Reads and replaces the whole database for backup and restore. Schema-agnostic: it walks the tables Room created,
 * so new tables are backed up without code here. Restore runs in one transaction, so it either fully lands or
 * leaves the current data untouched (PRD F-14.7).
 */
class DatabaseSnapshotter @Inject constructor(private val db: FuelwiseDatabase) {
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    fun dump(): DatabaseDump {
        val tables = userTables().associateWith { table ->
            sql.query("SELECT * FROM `$table`").use { cursor -> cursor.rows() }
        }
        return DatabaseDump(sql.version, tables)
    }

    /**
     * Replaces all rows with [dump]. Columns the current schema doesn't have are dropped and missing ones take their
     * defaults; a row that still can't be inserted, or a count that doesn't match, rolls everything back.
     */
    fun replaceAll(dump: DatabaseDump) {
        require(dump.schemaVersion <= sql.version) { "Backup is from a newer app version (${dump.schemaVersion})" }
        val tables = userTables()
        // Room's transaction, so every Flow re-reads once it commits; a throw rolls it all back.
        db.runInTransaction {
            // Only tables the backup carries are replaced: an unencrypted backup leaves the documents vault alone.
            tables.filter { it in dump.tables }.forEach { sql.execSQL("DELETE FROM `$it`") }
            dump.tables.filterKeys { it in tables }.forEach { (table, rows) ->
                val columns = columnsOf(table)
                rows.forEach { row ->
                    val values = ContentValues()
                    row.filterKeys { it in columns }.forEach { (column, value) -> values.putValue(column, value) }
                    sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
                }
                val count = sql.query("SELECT COUNT(*) FROM `$table`").use { c ->
                    c.moveToFirst()
                    c.getInt(0)
                }
                check(count == rows.size) { "Row count mismatch in $table: $count of ${rows.size}" }
            }
        }
    }

    /**
     * "Delete all data" (PRD F-18): empties every table in one transaction, tombstones included. Only a full reset may
     * do this; everyday deletes keep tombstones (CLAUDE.md rule 3).
     */
    fun clearAll() {
        val tables = userTables()
        db.runInTransaction { tables.forEach { sql.execSQL("DELETE FROM `$it`") } }
    }

    private fun userTables(): Set<String> = sql.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('android_metadata', 'room_master_table', 'room_table_modification_log')",
    ).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    private fun columnsOf(table: String): Set<String> = sql.query("PRAGMA table_info(`$table`)").use { c ->
        buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
    }
}

private fun Cursor.rows(): List<Map<String, Any?>> = buildList {
    while (moveToNext()) {
        add(
            (0 until columnCount).associate { i ->
                columnNames[i] to when (getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                    Cursor.FIELD_TYPE_BLOB -> getBlob(i)
                    else -> getString(i)
                }
            },
        )
    }
}

private fun ContentValues.putValue(column: String, value: Any?) {
    when (value) {
        null -> putNull(column)
        is Long -> put(column, value)
        is Int -> put(column, value)
        is Double -> put(column, value)
        is Boolean -> put(column, if (value) 1 else 0)
        is ByteArray -> put(column, value)
        else -> put(column, value.toString())
    }
}

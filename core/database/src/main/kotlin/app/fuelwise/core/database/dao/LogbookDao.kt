package app.fuelwise.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A row of the unified Logbook query: fill-ups/charges and expenses/tolls in one shape. */
data class LogbookRow(
    val id: String,
    val source: String,
    val kind: String,
    val vehicleId: String,
    val dateTime: Long,
    val amountPaise: Long,
    val quantity: Double?,
    val unitPricePaise: Long?,
    val odometerKm: Double?,
    val tankId: String?,
    val categoryId: String?,
    val place: String?,
    val notes: String?,
    val isFull: Boolean,
)

data class MonthTotal(val month: String, val totalPaise: Long)

/**
 * The Logbook timeline (PRD F-05), newest first, paged by Room for smooth scrolling over thousands of rows.
 * Filters: optional vehicle, a set of kinds (empty = all) and a search over station, plaza and notes.
 * The toll category ids are the fixed SystemCategory ids.
 */
@Dao
interface LogbookDao {
    @Query("SELECT * FROM ($UNION) $WHERE ORDER BY dateTime DESC, id DESC")
    fun pagedRows(
        vehicleId: String?,
        kinds: List<String>,
        allKinds: Boolean,
        query: String,
    ): PagingSource<Int, LogbookRow>

    @Query("SELECT * FROM ($UNION) $WHERE ORDER BY dateTime DESC, id DESC LIMIT :limit")
    fun recentRows(
        vehicleId: String?,
        kinds: List<String>,
        allKinds: Boolean,
        query: String,
        limit: Int,
    ): Flow<List<LogbookRow>>

    /** Totals per local calendar month ("2026-10"), for the sticky month headers. */
    @Query(
        """
        SELECT strftime('%Y-%m', dateTime / 1000, 'unixepoch', 'localtime') AS month, SUM(amountPaise) AS totalPaise
        FROM ($UNION) $WHERE GROUP BY month
        """,
    )
    fun monthTotals(vehicleId: String?, kinds: List<String>, allKinds: Boolean, query: String): Flow<List<MonthTotal>>
}

private const val UNION = """
            SELECT id, 'Refuel' AS source,
                CASE WHEN socEnd IS NOT NULL OR chargerType IS NOT NULL THEN 'Charge' ELSE 'Fuel' END AS kind,
                vehicleId, dateTime, paidPaise AS amountPaise, quantity, unitPricePaise, odometerKm, tankId,
                NULL AS categoryId, stationName AS place, notes, isFull
            FROM refuel WHERE deletedAt IS NULL
            UNION ALL
            SELECT id, 'Expense' AS source,
                CASE WHEN categoryId IN ('00000000-0000-7000-8000-000000000008', '00000000-0000-7000-8000-000000000009')
                    THEN 'Toll' ELSE 'Expense' END AS kind,
                vehicleId, dateTime, amountPaise, NULL AS quantity, NULL AS unitPricePaise, odometerKm, NULL AS tankId,
                categoryId, tollPlaza AS place, notes, 0 AS isFull
            FROM expense WHERE deletedAt IS NULL
        """

private const val WHERE = """
            WHERE (:vehicleId IS NULL OR vehicleId = :vehicleId)
              AND (:allKinds OR kind IN (:kinds))
              AND (:query = '' OR place LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        """

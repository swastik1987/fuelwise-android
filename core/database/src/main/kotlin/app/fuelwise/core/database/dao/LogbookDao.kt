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
    val paymentMethod: String? = null,
)

data class MonthTotal(val month: String, val totalPaise: Long)

/**
 * The Logbook timeline (PRD F-05), newest first, paged by Room for smooth scrolling over thousands of rows.
 * Filters: optional vehicle, a set of kinds (empty = all), expense categories and payment methods (empty = all), an
 * optional date range (UTC ms, from inclusive, to exclusive) and a search over station, plaza and notes.
 * The toll category ids are the fixed SystemCategory ids.
 */
// One parameter per filter is how Room binds them; a holder object can't be bound in a @Query.
@Suppress("LongParameterList")
@Dao
interface LogbookDao {
    @Query("SELECT * FROM ($UNION) $WHERE ORDER BY dateTime DESC, id DESC")
    fun pagedRows(
        vehicleId: String?,
        kinds: List<String>,
        allKinds: Boolean,
        categories: List<String>,
        allCategories: Boolean,
        payments: List<String>,
        allPayments: Boolean,
        fromMs: Long?,
        toMs: Long?,
        query: String,
    ): PagingSource<Int, LogbookRow>

    @Query("SELECT * FROM ($UNION) $WHERE ORDER BY dateTime DESC, id DESC LIMIT :limit")
    fun recentRows(
        vehicleId: String?,
        kinds: List<String>,
        allKinds: Boolean,
        categories: List<String>,
        allCategories: Boolean,
        payments: List<String>,
        allPayments: Boolean,
        fromMs: Long?,
        toMs: Long?,
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
    fun monthTotals(
        vehicleId: String?,
        kinds: List<String>,
        allKinds: Boolean,
        categories: List<String>,
        allCategories: Boolean,
        payments: List<String>,
        allPayments: Boolean,
        fromMs: Long?,
        toMs: Long?,
        query: String,
    ): Flow<List<MonthTotal>>
}

private const val UNION = """
            SELECT id, 'Refuel' AS source,
                CASE WHEN socEnd IS NOT NULL OR chargerType IS NOT NULL THEN 'Charge' ELSE 'Fuel' END AS kind,
                vehicleId, dateTime, paidPaise AS amountPaise, quantity, unitPricePaise, odometerKm, tankId,
                NULL AS categoryId, stationName AS place, notes, isFull, paymentMethod
            FROM refuel WHERE deletedAt IS NULL
            UNION ALL
            SELECT id, 'Expense' AS source,
                CASE WHEN categoryId IN ('00000000-0000-7000-8000-000000000008', '00000000-0000-7000-8000-000000000009')
                    THEN 'Toll' ELSE 'Expense' END AS kind,
                vehicleId, dateTime, amountPaise, NULL AS quantity, NULL AS unitPricePaise, odometerKm, NULL AS tankId,
                categoryId, tollPlaza AS place, notes, 0 AS isFull, paymentMethod
            FROM expense WHERE deletedAt IS NULL
        """

private const val WHERE = """
            WHERE (:vehicleId IS NULL OR vehicleId = :vehicleId)
              AND (:allKinds OR kind IN (:kinds))
              AND (:allCategories OR categoryId IN (:categories))
              AND (:allPayments OR paymentMethod IN (:payments))
              AND (:fromMs IS NULL OR dateTime >= :fromMs)
              AND (:toMs IS NULL OR dateTime < :toMs)
              AND (:query = '' OR place LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        """

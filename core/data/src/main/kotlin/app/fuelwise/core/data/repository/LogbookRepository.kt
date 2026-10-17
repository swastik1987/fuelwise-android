package app.fuelwise.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import app.fuelwise.core.data.mapper.enumValueOrNull
import app.fuelwise.core.database.dao.LogbookDao
import app.fuelwise.core.database.dao.LogbookRow
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.LogbookKind
import app.fuelwise.core.model.Money
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Logbook filters (design.md screen 12). Empty [kinds] means everything; null [vehicleId] means all vehicles. */
data class LogbookFilter(
    val vehicleId: String? = null,
    val kinds: Set<LogbookKind> = emptySet(),
    val query: String = "",
)

interface LogbookRepository {
    /** Newest first, paged (PRD F-05 AC: smooth with 5,000 entries). */
    fun pagedEntries(filter: LogbookFilter): Flow<PagingData<LogbookEntry>>

    /** Totals per local month, keyed "2026-10". */
    fun monthTotals(filter: LogbookFilter): Flow<Map<String, Money>>

    fun recentEntries(vehicleId: String?, limit: Int): Flow<List<LogbookEntry>>
}

internal class OfflineLogbookRepository @Inject constructor(private val dao: LogbookDao) : LogbookRepository {
    override fun pagedEntries(filter: LogbookFilter): Flow<PagingData<LogbookEntry>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            dao.pagedRows(filter.vehicleId, filter.kindNames(), filter.kinds.isEmpty(), filter.query.trim())
        }.flow.map { page -> page.map { it.toModel() } }

    override fun monthTotals(filter: LogbookFilter): Flow<Map<String, Money>> =
        dao.monthTotals(filter.vehicleId, filter.kindNames(), filter.kinds.isEmpty(), filter.query.trim())
            .map { rows -> rows.associate { it.month to Money(it.totalPaise) } }

    override fun recentEntries(vehicleId: String?, limit: Int): Flow<List<LogbookEntry>> =
        dao.recentRows(vehicleId, emptyList(), allKinds = true, query = "", limit = limit).map { rows ->
            rows.map { it.toModel() }
        }

    private fun LogbookFilter.kindNames() = kinds.map { it.name }

    private fun LogbookRow.toModel() = LogbookEntry(
        id = id,
        source = enumValueOrNull<EntrySource>(source) ?: EntrySource.Expense,
        kind = enumValueOrNull<LogbookKind>(kind) ?: LogbookKind.Expense,
        vehicleId = vehicleId,
        dateTimeUtcMs = dateTime,
        amount = Money(amountPaise),
        quantity = quantity,
        unitPrice = unitPricePaise?.let(::Money),
        odometerKm = odometerKm,
        tankId = tankId,
        categoryId = categoryId,
        place = place,
        notes = notes,
        isFull = isFull,
    )

    private companion object {
        const val PAGE_SIZE = 50
    }
}

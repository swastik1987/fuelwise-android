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
import app.fuelwise.core.model.PaymentMethod
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Logbook filters (design.md screen 12, PRD F-05). An empty set means no filter on that; null [vehicleId] means all
 * vehicles. [categoryIds] picks expenses by category (fill-ups have none, so they drop out). The date range is UTC ms,
 * [fromUtcMs] inclusive and [toUtcMs] exclusive.
 */
data class LogbookFilter(
    val vehicleId: String? = null,
    val kinds: Set<LogbookKind> = emptySet(),
    val query: String = "",
    val categoryIds: Set<String> = emptySet(),
    val payments: Set<PaymentMethod> = emptySet(),
    val fromUtcMs: Long? = null,
    val toUtcMs: Long? = null,
) {
    /** Anything beyond vehicle, kinds and search: what the filter sheet sets. */
    val hasExtraFilters: Boolean
        get() = categoryIds.isNotEmpty() || payments.isNotEmpty() || fromUtcMs != null || toUtcMs != null
}

interface LogbookRepository {
    /** Newest first, paged (PRD F-05 AC: smooth with 5,000 entries). */
    fun pagedEntries(filter: LogbookFilter): Flow<PagingData<LogbookEntry>>

    /** Totals per local month, keyed "2026-10". */
    fun monthTotals(filter: LogbookFilter): Flow<Map<String, Money>>

    fun recentEntries(vehicleId: String?, limit: Int): Flow<List<LogbookEntry>>

    /** Every entry under [filter], newest first, unpaged: the month headers' summaries need the whole month. */
    fun filteredEntries(filter: LogbookFilter): Flow<List<LogbookEntry>>
}

internal class OfflineLogbookRepository @Inject constructor(private val dao: LogbookDao) : LogbookRepository {
    override fun pagedEntries(filter: LogbookFilter): Flow<PagingData<LogbookEntry>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            with(filter) {
                dao.pagedRows(
                    vehicleId, kindNames(), kinds.isEmpty(), categoryIds.toList(), categoryIds.isEmpty(),
                    paymentNames(), payments.isEmpty(), fromUtcMs, toUtcMs, query.trim(),
                )
            }
        }.flow.map { page -> page.map { it.toModel() } }

    override fun monthTotals(filter: LogbookFilter): Flow<Map<String, Money>> = with(filter) {
        dao.monthTotals(
            vehicleId, kindNames(), kinds.isEmpty(), categoryIds.toList(), categoryIds.isEmpty(),
            paymentNames(), payments.isEmpty(), fromUtcMs, toUtcMs, query.trim(),
        )
    }
        .map { rows -> rows.associate { it.month to Money(it.totalPaise) } }

    override fun filteredEntries(filter: LogbookFilter): Flow<List<LogbookEntry>> = with(filter) {
        dao.recentRows(
            vehicleId, kindNames(), kinds.isEmpty(), categoryIds.toList(), categoryIds.isEmpty(),
            paymentNames(), payments.isEmpty(), fromUtcMs, toUtcMs, query.trim(), limit = NO_LIMIT,
        )
    }.map { rows -> rows.map { it.toModel() } }

    override fun recentEntries(vehicleId: String?, limit: Int): Flow<List<LogbookEntry>> = dao.recentRows(
        vehicleId, emptyList(), allKinds = true, categories = emptyList(), allCategories = true,
        payments = emptyList(), allPayments = true, fromMs = null, toMs = null, query = "", limit = limit,
    ).map { rows ->
        rows.map { it.toModel() }
    }

    private fun LogbookFilter.kindNames() = kinds.map { it.name }

    private fun LogbookFilter.paymentNames() = payments.map { it.name }

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

        /** SQLite: a negative LIMIT means no limit. */
        const val NO_LIMIT = -1
    }
}

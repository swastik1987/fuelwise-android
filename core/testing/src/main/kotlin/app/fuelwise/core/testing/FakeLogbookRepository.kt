package app.fuelwise.core.testing

import androidx.paging.PagingData
import app.fuelwise.core.data.repository.LogbookFilter
import app.fuelwise.core.data.repository.LogbookRepository
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.Money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Logbook view over a [FakeEntryRepository], newest first, like the Room UNION query. */
class FakeLogbookRepository(
    private val entries: FakeEntryRepository,
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata"),
) : LogbookRepository {
    private fun all(filter: LogbookFilter): Flow<List<LogbookEntry>> =
        combine(entries.observeAllRefuels(), entries.observeAllExpenses()) { refuels, expenses ->
            val payments =
                refuels.associate { it.id to it.paymentMethod } + expenses.associate { it.id to it.paymentMethod }
            (refuels.map(LogbookEntry::of) + expenses.map(LogbookEntry::of))
                .filter { filter.categoryIds.isEmpty() || it.categoryId in filter.categoryIds }
                .filter { filter.payments.isEmpty() || payments[it.id] in filter.payments }
                .filter { e -> filter.fromUtcMs?.let { e.dateTimeUtcMs >= it } ?: true }
                .filter { e -> filter.toUtcMs?.let { e.dateTimeUtcMs < it } ?: true }
                .filter { filter.vehicleId == null || it.vehicleId == filter.vehicleId }
                .filter { filter.kinds.isEmpty() || it.kind in filter.kinds }
                .filter { e ->
                    filter.query.isBlank() ||
                        listOfNotNull(e.place, e.notes).any { it.contains(filter.query, ignoreCase = true) }
                }
                .sortedByDescending { it.dateTimeUtcMs }
        }

    override fun pagedEntries(filter: LogbookFilter): Flow<PagingData<LogbookEntry>> = all(filter).map {
        PagingData.from(it)
    }

    override fun monthTotals(filter: LogbookFilter): Flow<Map<String, Money>> = all(filter).map { list ->
        list.groupBy { YearMonth.from(Instant.ofEpochMilli(it.dateTimeUtcMs).atZone(zone)).toString() }
            .mapValues { (_, month) -> Money(month.sumOf { it.amount.paise }) }
    }

    override fun recentEntries(vehicleId: String?, limit: Int): Flow<List<LogbookEntry>> =
        all(LogbookFilter(vehicleId = vehicleId)).map { it.take(limit) }
}

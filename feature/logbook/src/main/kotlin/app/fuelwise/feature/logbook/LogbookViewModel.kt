package app.fuelwise.feature.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.DeletedEntry
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.LogbookFilter
import app.fuelwise.core.data.repository.LogbookRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.entry.EntryFacts
import app.fuelwise.core.domain.entry.EntryRangePreset
import app.fuelwise.core.domain.entry.LogbookFacts
import app.fuelwise.core.domain.entry.MonthSummary
import app.fuelwise.core.domain.entry.entryRange
import app.fuelwise.core.domain.units.UnitSettings
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.LogbookKind
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A paged Logbook item: a month header or an entry (design.md screen 12). */
sealed interface LogbookItem {
    data class Header(val month: YearMonth) : LogbookItem

    data class Row(val entry: LogbookEntry) : LogbookItem
}

data class LogbookChrome(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val allVehicles: Boolean = false,
    val kinds: Set<LogbookKind> = emptySet(),
    val query: String = "",
    val searchOpen: Boolean = false,
    val categories: Map<String, Category> = emptyMap(),
    /** Per-entry card figures (design "Logbook entry card", 23 Nov 2026). */
    val facts: Map<String, EntryFacts> = emptyMap(),
    val monthTotals: Map<String, Money> = emptyMap(),
    /** The month header's right side, by type filter, keyed like [monthTotals]. */
    val monthSummaries: Map<String, MonthSummary> = emptyMap(),
    /** Month headers show the year only for other years. */
    val currentYear: Int = 0,
    /** Map mode (PRD F-05; design.md 12b): geotagged fill-ups and charges under the same filters. */
    val mapMode: Boolean = false,
    val pins: List<LogbookPin> = emptyList(),
    val selectedPinId: String? = null,
    /** Display units per vehicle (PRD F-18). */
    val units: UnitSettings = UnitSettings(),
    /** The filter sheet (PRD F-05): categories, payment methods and a date range. */
    val sheet: LogbookSheetFilters = LogbookSheetFilters(),
) {
    val filtering: Boolean get() = kinds.isNotEmpty() || query.isNotBlank() || sheet.count > 0
}

/** What the filter sheet sets. Empty sets mean "all". */
data class LogbookSheetFilters(
    val categoryIds: Set<String> = emptySet(),
    val payments: Set<PaymentMethod> = emptySet(),
    val range: EntryRangePreset = EntryRangePreset.All,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
) {
    /** How many groups are narrowed, for the "Filters · 2" chip. */
    val count: Int get() = listOf(
        categoryIds.isNotEmpty(),
        payments.isNotEmpty(),
        range != EntryRangePreset.All,
    ).count {
        it
    }
}

/** A geotagged fill-up or charge on the Logbook map. */
data class LogbookPin(val entry: LogbookEntry, val lat: Double, val lng: Double, val energy: EnergyType?)

sealed interface LogbookEvent {
    data class Deleted(val token: DeletedEntry) : LogbookEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogbookViewModel @Inject constructor(
    private val logbook: LogbookRepository,
    private val entries: EntryRepository,
    vehicles: VehicleRepository,
    categories: CategoryRepository,
    private val userData: UserDataRepository,
    private val logbookFacts: LogbookFacts,
    private val zoneId: ZoneId,
    private val clock: Clock,
) : ViewModel() {
    private data class Controls(
        val allVehicles: Boolean = false,
        val kinds: Set<LogbookKind> = emptySet(),
        val query: String = "",
        val searchOpen: Boolean = false,
        val mapMode: Boolean = false,
        val selectedPinId: String? = null,
        val sheet: LogbookSheetFilters = LogbookSheetFilters(),
    )

    private val controls = MutableStateFlow(Controls())

    private class Cards(val refuels: List<Refuel>, val facts: Map<String, EntryFacts>)

    private class Months(val totals: Map<String, Money>, val summaries: Map<String, MonthSummary>)

    private val selectedVehicle: Flow<Triple<List<Vehicle>, Vehicle?, UnitSettings>> =
        combine(vehicles.observeVehicles(includeArchived = true), userData.userData) { all, data ->
            val active = all.filterNot { it.isArchived }
            Triple(all, active.firstOrNull { it.id == data.selectedVehicleId } ?: active.firstOrNull(), data.units)
        }

    private val filter: Flow<LogbookFilter> = combine(selectedVehicle, controls) { (_, selected, _), c ->
        val range =
            with(c.sheet) { entryRange(range, LocalDate.now(clock.withZone(zoneId)), zoneId, customFrom, customTo) }
        LogbookFilter(
            vehicleId = if (c.allVehicles) null else selected?.id,
            kinds = c.kinds,
            query = c.query,
            categoryIds = c.sheet.categoryIds,
            payments = c.sheet.payments,
            fromUtcMs = range.fromUtcMs,
            toUtcMs = range.toUtcMs,
        )
    }

    val items: Flow<PagingData<LogbookItem>> = filter
        .flatMapLatest { logbook.pagedEntries(it) }
        .map { it.withMonthHeaders(zoneId) }
        .cachedIn(viewModelScope)

    private val cards: Flow<Cards> = combine(
        entries.observeAllRefuels(),
        entries.observeAllExpenses(),
        selectedVehicle,
    ) { refuels, expenses, (all, _, _) ->
        Cards(refuels, logbookFacts(refuels, expenses, all, zoneId))
    }

    private val months: Flow<Months> = combine(
        filter.flatMapLatest { f -> combine(logbook.monthTotals(f), logbook.filteredEntries(f)) { t, e -> t to e } },
        controls,
        cards,
    ) { (totals, filtered), c, cards ->
        val summaries = MonthSummary.of(filtered, c.kinds, cards.facts, zoneId)
        Months(totals, summaries.mapKeys { it.key.toString() })
    }

    val chrome: StateFlow<LogbookChrome> = combine(
        selectedVehicle,
        controls,
        categories.observeCategories(),
        cards,
        months,
    ) { (all, selected, units), c, cats, cards, months ->
        val refuels = cards.refuels
        val vehicleId = if (c.allVehicles) null else selected?.id
        LogbookChrome(
            vehicles = all,
            selectedVehicle = selected,
            allVehicles = c.allVehicles,
            kinds = c.kinds,
            query = c.query,
            searchOpen = c.searchOpen,
            categories = cats.associateBy { it.id },
            facts = cards.facts,
            monthTotals = months.totals,
            monthSummaries = months.summaries,
            currentYear = Year.now(clock.withZone(zoneId)).value,
            mapMode = c.mapMode,
            pins = if (c.mapMode) pins(refuels, all, vehicleId, c) else emptyList(),
            selectedPinId = c.selectedPinId,
            units = units,
            sheet = c.sheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LogbookChrome())

    private val eventChannel = Channel<LogbookEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun toggleKind(kind: LogbookKind) = controls.update { c ->
        c.copy(
            kinds = if (kind in
                c.kinds
            ) {
                c.kinds - kind
            } else {
                c.kinds + kind
            },
        )
    }

    fun clearKinds() = controls.update { it.copy(kinds = emptySet()) }

    /** Applies the filter sheet (PRD F-05: category, payment method, date range). */
    fun setSheetFilters(filters: LogbookSheetFilters) = controls.update { it.copy(sheet = filters) }

    fun setQuery(query: String) = controls.update { it.copy(query = query.take(MAX_QUERY)) }

    fun toggleMap() = controls.update { it.copy(mapMode = !it.mapMode, selectedPinId = null) }

    fun selectPin(id: String?) = controls.update { it.copy(selectedPinId = id) }

    fun toggleSearch() = controls.update {
        it.copy(searchOpen = !it.searchOpen, query = if (it.searchOpen) "" else it.query)
    }

    /** null = "All vehicles". */
    fun selectVehicle(id: String?) {
        controls.update { it.copy(allVehicles = id == null) }
        if (id != null) viewModelScope.launch { userData.setSelectedVehicle(id) }
    }

    fun delete(entry: LogbookEntry) {
        viewModelScope.launch {
            val token = when (entry.source) {
                EntrySource.Refuel -> entries.deleteRefuel(entry.id)
                EntrySource.Expense -> entries.deleteExpense(entry.id)
            }
            eventChannel.send(LogbookEvent.Deleted(token))
        }
    }

    fun undo(token: DeletedEntry) {
        viewModelScope.launch { entries.undoDelete(token) }
    }

    // The same vehicle, kind and text filters as the list, for entries that carry a location.
    private fun pins(
        refuels: List<Refuel>,
        vehicles: List<Vehicle>,
        vehicleId: String?,
        c: Controls,
    ): List<LogbookPin> {
        val tanks = vehicles.flatMap { it.tanks }.associateBy { it.id }
        val query = c.query.trim()
        return refuels.mapNotNull { refuel ->
            val lat = refuel.lat ?: return@mapNotNull null
            val lng = refuel.lng ?: return@mapNotNull null
            val entry = LogbookEntry.of(refuel)
            val matches = (vehicleId == null || refuel.vehicleId == vehicleId) &&
                (c.kinds.isEmpty() || entry.kind in c.kinds) &&
                (query.isEmpty() || listOfNotNull(refuel.stationName, refuel.notes).any { it.contains(query, true) })
            if (matches) LogbookPin(entry, lat, lng, tanks[refuel.tankId]?.energyType) else null
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_QUERY = 60
    }
}

/** Newest-first entries with a [LogbookItem.Header] before the first entry of each month (sticky headers). */
internal fun PagingData<LogbookEntry>.withMonthHeaders(zone: ZoneId): PagingData<LogbookItem> {
    fun LogbookEntry.month(): YearMonth = YearMonth.from(Instant.ofEpochMilli(dateTimeUtcMs).atZone(zone))
    return map<LogbookEntry, LogbookItem> { LogbookItem.Row(it) }.insertSeparators { before, after ->
        val afterMonth = (after as? LogbookItem.Row)?.entry?.month() ?: return@insertSeparators null
        val beforeMonth = (before as? LogbookItem.Row)?.entry?.month()
        if (beforeMonth != afterMonth) LogbookItem.Header(afterMonth) else null
    }
}

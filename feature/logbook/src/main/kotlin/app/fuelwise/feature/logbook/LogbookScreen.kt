package app.fuelwise.feature.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.LogbookKind
import app.fuelwise.core.ui.entry.LogbookEntryRow
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.map.FwPinMap
import app.fuelwise.core.ui.map.MapPin
import app.fuelwise.core.ui.vehicle.AllVehiclesPill
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.core.ui.vehicle.VehicleSwitcherPill
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.format.TextStyle
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun LogbookScreen(
    onAddEntry: () -> Unit,
    onOpenEntry: (EntrySource, String) -> Unit,
    onEditEntry: (EntrySource, String) -> Unit,
    viewModel: LogbookViewModel = hiltViewModel(),
) {
    val chrome by viewModel.chrome.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LogbookEvent.Deleted -> {
                    // Same 10-second undo as vehicle delete (PRD F-02 AC).
                    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                        snackbar.showSnackbar(
                            resources.getString(R.string.logbook_deleted),
                            resources.getString(R.string.logbook_undo),
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == null) snackbar.currentSnackbarData?.dismiss()
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.token)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        LogbookContent(
            chrome = chrome,
            items = items,
            actions = LogbookActions(
                onAddEntry = onAddEntry,
                onOpen = { onOpenEntry(it.source, it.id) },
                onEdit = { onEditEntry(it.source, it.id) },
                onDelete = viewModel::delete,
                onToggleKind = viewModel::toggleKind,
                onClearKinds = viewModel::clearKinds,
                onQuery = viewModel::setQuery,
                onToggleSearch = viewModel::toggleSearch,
                onSelectVehicle = viewModel::selectVehicle,
                onToggleMap = viewModel::toggleMap,
                onSelectPin = viewModel::selectPin,
                onSheetFilters = viewModel::setSheetFilters,
            ),
        )
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

internal data class LogbookActions(
    val onAddEntry: () -> Unit = {},
    val onOpen: (LogbookEntry) -> Unit = {},
    val onEdit: (LogbookEntry) -> Unit = {},
    val onDelete: (LogbookEntry) -> Unit = {},
    val onToggleKind: (LogbookKind) -> Unit = {},
    val onClearKinds: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onToggleSearch: () -> Unit = {},
    val onSelectVehicle: (String?) -> Unit = {},
    val onToggleMap: () -> Unit = {},
    val onSelectPin: (String?) -> Unit = {},
    val onSheetFilters: (LogbookSheetFilters) -> Unit = {},
)

@Composable
internal fun LogbookContent(chrome: LogbookChrome, items: LazyPagingItems<LogbookItem>, actions: LogbookActions) {
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(
                WindowInsets.statusBars,
            ).height(64.dp).padding(start = FwTheme.spacing.gutter, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val vehicle = chrome.selectedVehicle
            if (chrome.allVehicles || vehicle == null) {
                AllVehiclesPill(onClick = { switcherOpen = true })
            } else {
                VehicleSwitcherPill(vehicle, onClick = { switcherOpen = true })
            }
            Box(Modifier.weight(1f))
            // List ⇄ map (design.md screen 12).
            IconButton(onClick = actions.onToggleMap) {
                Icon(
                    painterResource(if (chrome.mapMode) FwIcons.ListView else FwIcons.Map),
                    contentDescription = stringResource(
                        if (chrome.mapMode) R.string.logbook_show_list else R.string.logbook_show_map,
                    ),
                    tint = FwTheme.colors.ink,
                )
            }
            IconButton(onClick = actions.onToggleSearch) {
                Icon(
                    painterResource(if (chrome.searchOpen) FwIcons.Close else FwIcons.Search),
                    contentDescription = stringResource(
                        if (chrome.searchOpen) R.string.logbook_search_close else R.string.logbook_search,
                    ),
                    tint = FwTheme.colors.ink,
                )
            }
        }
        if (chrome.searchOpen) {
            FwTextField(
                value = chrome.query,
                onValueChange = actions.onQuery,
                label = stringResource(R.string.logbook_search_label),
                modifier = Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = 4.dp),
            )
        }
        FilterRow(chrome, actions)
        if (chrome.mapMode) {
            LogbookMap(chrome, actions)
        } else if (items.itemCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (chrome.filtering) {
                    Text(
                        stringResource(R.string.logbook_no_matches),
                        style = FwTheme.type.body,
                        color = FwTheme.colors.ink2,
                    )
                } else {
                    FwEmptyState(
                        icon = FwIcons.Logbook,
                        headline = stringResource(R.string.logbook_empty_title),
                        body = stringResource(R.string.logbook_empty_body),
                        actionLabel = stringResource(R.string.logbook_empty_action),
                        onAction = actions.onAddEntry,
                    )
                }
            }
        } else {
            LogbookList(chrome, items, actions)
        }
    }
    if (switcherOpen) {
        VehicleSwitcherSheet(
            vehicles = chrome.vehicles.filterNot { it.isArchived },
            selectedId = if (chrome.allVehicles) null else chrome.selectedVehicle?.id,
            onSelect = {
                actions.onSelectVehicle(it)
                switcherOpen = false
            },
            onAddVehicle = { switcherOpen = false },
            onDismiss = { switcherOpen = false },
            showAllVehicles = true,
        )
    }
}

/**
 * Map mode (PRD F-05; design.md 12b): geotagged fill-ups and charges as energy-coloured pins, under the same filters
 * as the list. Tapping a pin shows its entry in a card. Entries get a location from station auto-detect (F-21.1).
 */
@Composable
private fun LogbookMap(chrome: LogbookChrome, actions: LogbookActions) {
    val vehicles = chrome.vehicles.associateBy { it.id }
    val selected = chrome.pins.firstOrNull { it.entry.id == chrome.selectedPinId }
    Box(
        Modifier.fillMaxSize().padding(start = FwTheme.spacing.gutter, end = FwTheme.spacing.gutter, top = 8.dp),
    ) {
        FwPinMap(
            pins = chrome.pins.map { pin ->
                val color = pin.energy?.let { VehicleLabels.energyColor(it) } ?: FwTheme.colors.ink
                MapPin(pin.entry.id, pin.lat, pin.lng, color)
            },
            contentDescription = pluralStringResource(R.plurals.logbook_map_desc, chrome.pins.size, chrome.pins.size),
            onSelect = actions.onSelectPin,
            modifier = Modifier.fillMaxSize().clip(FwTheme.radius.card),
        )
        if (chrome.pins.isEmpty()) {
            FwCard(Modifier.align(Alignment.Center).padding(FwTheme.spacing.l)) {
                Text(stringResource(R.string.logbook_map_empty), style = FwTheme.type.label, color = FwTheme.colors.ink)
                Text(
                    stringResource(R.string.logbook_map_empty_body),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        selected?.let { pin ->
            FwCard(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = FwTheme.spacing.m),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                LogbookEntryRow(
                    entry = pin.entry,
                    vehicle = vehicles[pin.entry.vehicleId],
                    categories = chrome.categories,
                    kmPerUnit = chrome.kmPerUnit[pin.entry.id],
                    units = chrome.units.forVehicle(pin.entry.vehicleId),
                    onClick = { actions.onOpen(pin.entry) },
                    showVehicleName = chrome.allVehicles,
                )
            }
        }
    }
}

@Composable
private fun FilterRow(chrome: LogbookChrome, actions: LogbookActions) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = FwTheme.spacing.gutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Date range, category and payment (PRD F-05) live in a sheet; the chip shows how many are set.
        val count = chrome.sheet.count
        FwFilterChip(
            if (count ==
                0
            ) {
                stringResource(R.string.logbook_filters)
            } else {
                stringResource(R.string.logbook_filters_count, count)
            },
            count > 0,
            { sheetOpen = true },
        )
        FwFilterChip(stringResource(R.string.logbook_filter_all), chrome.kinds.isEmpty(), actions.onClearKinds)
        LogbookKind.entries.forEach { kind ->
            FwFilterChip(stringResource(kindLabel(kind)), kind in chrome.kinds, { actions.onToggleKind(kind) })
        }
    }
    if (sheetOpen) {
        LogbookFilterSheet(
            current = chrome.sheet,
            categories = chrome.categories.values.sortedBy { it.sortOrder },
            onApply = {
                actions.onSheetFilters(it)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogbookList(chrome: LogbookChrome, items: LazyPagingItems<LogbookItem>, actions: LogbookActions) {
    val vehicles = chrome.vehicles.associateBy { it.id }
    LazyColumn(Modifier.fillMaxSize()) {
        for (index in 0 until items.itemCount) {
            when (val peeked = items.peek(index)) {
                is LogbookItem.Header -> stickyHeader(key = "h-${peeked.month}") {
                    val month = items[index] as? LogbookItem.Header ?: peeked
                    MonthHeader(month, chrome.monthTotals[month.month.toString()], chrome.currentYear)
                }

                is LogbookItem.Row, null -> item(key = peeked?.entry?.let { "${it.source}-${it.id}" } ?: "p-$index") {
                    val row = items[index] as? LogbookItem.Row ?: return@item
                    SwipeableRow(
                        entry = row.entry,
                        actions = actions,
                    ) {
                        LogbookEntryRow(
                            entry = row.entry,
                            vehicle = vehicles[row.entry.vehicleId],
                            categories = chrome.categories,
                            kmPerUnit = chrome.kmPerUnit[row.entry.id],
                            units = chrome.units.forVehicle(row.entry.vehicleId),
                            onClick = { actions.onOpen(row.entry) },
                            showVehicleName = chrome.allVehicles,
                            modifier = Modifier.background(
                                FwTheme.colors.bg,
                            ).padding(horizontal = FwTheme.spacing.gutter),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(entry: LogbookEntry, actions: LogbookActions, content: @Composable () -> Unit) {
    val editLabel = stringResource(R.string.logbook_edit)
    val deleteLabel = stringResource(R.string.logbook_delete)
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> actions.onDelete(entry)

            SwipeToDismissBoxValue.StartToEnd -> {
                actions.onEdit(entry)
                state.reset()
            }

            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = state,
        // Swipes also exist as TalkBack actions, since swiping is hard with a screen reader.
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(editLabel) {
                    actions.onEdit(entry)
                    true
                },
                CustomAccessibilityAction(deleteLabel) {
                    actions.onDelete(entry)
                    true
                },
            )
        },
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier.fillMaxSize().background(
                    if (toDelete) FwTheme.colors.negative else FwTheme.colors.surface2,
                ).padding(horizontal = FwTheme.spacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDelete) Arrangement.End else Arrangement.Start,
            ) {
                Icon(
                    painterResource(if (toDelete) FwIcons.Delete else FwIcons.Edit),
                    contentDescription = null,
                    tint = if (toDelete) Color.White else FwTheme.colors.ink,
                )
            }
        },
    ) { content() }
}

@Composable
private fun MonthHeader(header: LogbookItem.Header, total: app.fuelwise.core.model.Money?, currentYear: Int) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.fillMaxWidth().background(
            FwTheme.colors.bg,
        ).heightIn(min = 44.dp).padding(horizontal = FwTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            header.month.month.getDisplayName(TextStyle.FULL, locale) +
                if (header.month.year != currentYear) " ${header.month.year}" else "",
            style = FwTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
            color = FwTheme.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        total?.let { Text(IndianNumberFormat.rupeesWhole(it), style = FwTheme.type.label, color = FwTheme.colors.ink2) }
    }
}

private fun kindLabel(kind: LogbookKind): Int = when (kind) {
    LogbookKind.Fuel -> R.string.logbook_filter_fuel
    LogbookKind.Charge -> R.string.logbook_filter_charge
    LogbookKind.Expense -> R.string.logbook_filter_expenses
    LogbookKind.Toll -> R.string.logbook_filter_tolls
}

private const val UNDO_WINDOW_MS = 10_000L

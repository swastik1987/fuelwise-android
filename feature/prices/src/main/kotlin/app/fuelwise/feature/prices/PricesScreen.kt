package app.fuelwise.feature.prices

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.Delta
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwDeltaChip
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSectionHeader
import app.fuelwise.core.designsystem.component.FwSparkline
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.prices.FuelPriceRow
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.price.PriceLabels
import java.time.LocalDate
import kotlin.math.abs

internal data class PricesActions(
    val onBack: () -> Unit = {},
    val onSetHome: (String) -> Unit = {},
    val onPin: (String) -> Unit = {},
    val onUnpin: (String) -> Unit = {},
    /** City, fuel, date and typed amount; returns false when the amount wasn't a price. */
    val onSavePrice: (String, PriceFuel, LocalDate, String) -> Boolean = { _, _, _, _ -> true },
    val onDeletePrice: (String) -> Unit = {},
    val onAlerts: (Boolean) -> Unit = {},
    val onFindPump: () -> Unit = {},
)

@Composable
internal fun PricesScreen(onBack: () -> Unit, pickCity: Boolean, viewModel: PricesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PricesEvent.OpenMaps -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.prices_no_maps, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    PricesContent(
        state = state,
        actions = PricesActions(
            onBack = onBack,
            onSetHome = viewModel::setHome,
            onPin = viewModel::pin,
            onUnpin = viewModel::unpin,
            onSavePrice = viewModel::savePrice,
            onDeletePrice = viewModel::deletePrice,
            onAlerts = viewModel::setAlerts,
            onFindPump = viewModel::findPump,
        ),
        pickCityOnStart = pickCity,
    )
}

/** What a sheet is open for. */
private sealed interface PricesSheet {
    data object PickHome : PricesSheet

    data object PickPinned : PricesSheet

    data class Edit(val cityId: String, val cityName: String, val fuel: PriceFuel) : PricesSheet
}

/** Fuel prices (design.md screen 25): city, a row per fuel, find a pump, compare cities, alerts. */
@Composable
internal fun PricesContent(state: PricesUiState, actions: PricesActions, pickCityOnStart: Boolean = false) {
    var sheetKey by rememberSaveable { mutableStateOf(if (pickCityOnStart) SHEET_HOME else "") }
    val sheet: PricesSheet? = sheetFor(sheetKey, state)
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.prices_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.prices_back),
        )
        if (state.loading) return@Column
        val home = state.home
        if (home == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FwEmptyState(
                    icon = FwIcons.Fuel,
                    headline = stringResource(R.string.prices_no_city_title),
                    body = stringResource(R.string.prices_no_city_body),
                    actionLabel = stringResource(R.string.prices_no_city_action),
                    onAction = { sheetKey = SHEET_HOME },
                )
            }
        } else {
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
            ) {
                HomeCity(home, state, onChange = { sheetKey = SHEET_HOME }) { fuel ->
                    sheetKey = editKey(home.city.id, fuel)
                }
                FindPump(state.energy, actions.onFindPump)
                Compare(state, actions, onAdd = { sheetKey = SHEET_PINNED }) { cityId, fuel ->
                    sheetKey = editKey(cityId, fuel)
                }
                FwCard(contentPadding = PaddingValues(horizontal = 18.dp)) {
                    FwSwitchRow(
                        title = stringResource(R.string.prices_alerts_title),
                        checked = state.alerts,
                        onCheckedChange = actions.onAlerts,
                        subtitle = stringResource(
                            if (state.feedEnabled) R.string.prices_alerts_body else R.string.prices_alerts_no_feed,
                        ),
                    )
                }
            }
        }
    }
    when (sheet) {
        null -> Unit

        PricesSheet.PickHome -> CityPickerSheet(
            catalog = state.catalog,
            exclude = emptySet(),
            onPick = {
                actions.onSetHome(it)
                sheetKey = ""
            },
            onDismiss = { sheetKey = "" },
        )

        PricesSheet.PickPinned -> CityPickerSheet(
            catalog = state.catalog,
            exclude = (listOfNotNull(state.home?.city?.id) + state.pinned.map { it.city.id }).toSet(),
            onPick = {
                actions.onPin(it)
                sheetKey = ""
            },
            onDismiss = { sheetKey = "" },
        )

        is PricesSheet.Edit -> {
            val row = (listOfNotNull(state.home) + state.pinned).firstOrNull { it.city.id == sheet.cityId }
                ?.summary?.row(sheet.fuel)
            PriceEntrySheet(
                cityName = sheet.cityName,
                fuel = sheet.fuel,
                today = state.today,
                existing = row?.latest?.takeIf { it.date == state.today },
                onSave = { date, text ->
                    if (actions.onSavePrice(sheet.cityId, sheet.fuel, date, text)) sheetKey = ""
                },
                onDelete = { id ->
                    actions.onDeletePrice(id)
                    sheetKey = ""
                },
                onDismiss = { sheetKey = "" },
            )
        }
    }
}

@Composable
private fun HomeCity(home: CityPrices, state: PricesUiState, onChange: () -> Unit, onEdit: (PriceFuel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                FwOverline(stringResource(R.string.prices_home_city))
                Text(home.city.name, style = FwTheme.type.h2, color = FwTheme.colors.ink)
                Text(home.city.state, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
            }
            val changeCd = stringResource(R.string.prices_change_city_cd, home.city.name)
            FwTextButton(
                stringResource(R.string.prices_change_city),
                onChange,
                Modifier.semantics { contentDescription = changeCd },
            )
        }
        FwCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            home.summary.rows.forEach { row -> FuelRow(row, state.today) { onEdit(row.fuel) } }
        }
        home.summary.updatedOn?.let { day ->
            Text(
                stringResource(
                    if (state.fromFeed(home)) R.string.prices_source_feed else R.string.prices_source_manual,
                    IndianNumberFormat.date(day),
                ),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
            )
        }
    }
}

/** Large row per fuel: price, change since the last revision, 30-day line (design.md screen 25). */
@Composable
private fun FuelRow(row: FuelPriceRow, today: LocalDate, onClick: () -> Unit) {
    val name = stringResource(PriceLabels.fuel(row.fuel))
    val unit = stringResource(PriceLabels.unit(row.fuel))
    val latest = row.latest
    val cd = if (latest == null) {
        stringResource(R.string.prices_row_empty_cd, name)
    } else {
        stringResource(R.string.prices_row_cd, name, IndianNumberFormat.rupees(latest.price), unit)
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = cd }) {
            Text(name, style = FwTheme.type.label, color = FwTheme.colors.ink, modifier = Modifier.weight(1f))
            if (latest == null) {
                Icon(
                    painterResource(FwIcons.Add),
                    contentDescription = null,
                    tint = FwTheme.colors.ink2,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    stringResource(R.string.prices_per_unit, IndianNumberFormat.rupees(latest.price), unit),
                    style = FwTheme.type.h3,
                    color = FwTheme.colors.ink,
                )
            }
        }
        if (latest == null) {
            Text(stringResource(R.string.prices_no_price), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
            return@Column
        }
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val previous = row.previous
            val change = row.change
            Box(Modifier.weight(1f)) {
                if (previous != null && change != null) {
                    PriceDelta(change, IndianNumberFormat.date(previous.date))
                } else if (latest.date != today) {
                    Text(
                        IndianNumberFormat.date(latest.date),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink2,
                    )
                }
            }
            if (row.sparkline.isNotEmpty()) {
                FwSparkline(
                    row.sparkline.map { it.paise.toDouble() },
                    Modifier.width(96.dp).clearAndSetSemantics {},
                    color = FwTheme.colors.ink2,
                )
            }
        }
    }
}

/** Up is costlier (negative tone), down is cheaper; the arrow carries the meaning, not the colour. */
@Composable
private fun PriceDelta(change: Money, sinceDate: String) {
    if (change.paise == 0L) {
        Text(
            stringResource(R.string.prices_no_change_since, sinceDate),
            style = FwTheme.type.caption,
            color = FwTheme.colors.ink2,
        )
        return
    }
    FwDeltaChip(
        Delta(
            label = stringResource(
                R.string.prices_change_since,
                IndianNumberFormat.rupees(Money(abs(change.paise))),
                sinceDate,
            ),
            direction = if (change.paise > 0) Delta.Direction.Up else Delta.Direction.Down,
            isImprovement = change.paise < 0,
        ),
    )
}

@Composable
private fun FindPump(energy: EnergyType?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FwSecondaryButton(
            stringResource(
                when (energy) {
                    EnergyType.CNG -> R.string.prices_find_cng
                    EnergyType.ELECTRIC -> R.string.prices_find_charger
                    else -> R.string.prices_find_pump
                },
            ),
            onClick,
            Modifier.fillMaxWidth(),
            leadingIcon = if (energy == EnergyType.ELECTRIC) FwIcons.Charge else FwIcons.Fuel,
        )
        Text(stringResource(R.string.prices_find_note), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
    }
}

@Composable
private fun Compare(
    state: PricesUiState,
    actions: PricesActions,
    onAdd: () -> Unit,
    onEdit: (String, PriceFuel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FwSectionHeader(
            stringResource(R.string.prices_compare),
            actionLabel = if (state.canPin) stringResource(R.string.prices_compare_add) else null,
            onAction = if (state.canPin) onAdd else null,
        )
        if (state.pinned.isEmpty()) {
            Text(stringResource(R.string.prices_compare_body), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
        }
        state.pinned.forEach { pinned ->
            FwCard(contentPadding = PaddingValues(start = 18.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pinned.city.name, style = FwTheme.type.label, color = FwTheme.colors.ink)
                        Text(pinned.city.state, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
                    }
                    IconButton(onClick = { actions.onUnpin(pinned.city.id) }) {
                        Icon(
                            painterResource(FwIcons.Close),
                            stringResource(R.string.prices_unpin_cd, pinned.city.name),
                            tint = FwTheme.colors.ink2,
                        )
                    }
                }
                pinned.summary.rows.forEach { row -> CompactRow(row) { onEdit(pinned.city.id, row.fuel) } }
            }
        }
    }
}

@Composable
private fun CompactRow(row: FuelPriceRow, onClick: () -> Unit) {
    val name = stringResource(PriceLabels.fuel(row.fuel))
    val unit = stringResource(PriceLabels.unit(row.fuel))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = FwTheme.type.body, color = FwTheme.colors.ink2, modifier = Modifier.weight(1f))
        Text(
            row.latest?.let { stringResource(R.string.prices_per_unit, IndianNumberFormat.rupees(it.price), unit) }
                ?: stringResource(R.string.prices_add),
            style = FwTheme.type.label,
            color = if (row.latest == null) FwTheme.colors.ink2 else FwTheme.colors.ink,
        )
    }
}

private fun sheetFor(key: String, state: PricesUiState): PricesSheet? = when {
    key == SHEET_HOME -> PricesSheet.PickHome

    key == SHEET_PINNED -> PricesSheet.PickPinned

    key.startsWith(SHEET_EDIT) -> {
        val (cityId, fuel) = key.removePrefix(SHEET_EDIT).split(':').let { it[0] to PriceFuel.valueOf(it[1]) }
        val city = (listOfNotNull(state.home) + state.pinned).firstOrNull { it.city.id == cityId }?.city
        city?.let { PricesSheet.Edit(cityId, it.name, fuel) }
    }

    else -> null
}

private fun editKey(cityId: String, fuel: PriceFuel) = "$SHEET_EDIT$cityId:${fuel.name}"

// Sheets are keyed by strings so the open sheet survives rotation (rememberSaveable).
private const val SHEET_HOME = "home"
private const val SHEET_PINNED = "pinned"
private const val SHEET_EDIT = "edit:"

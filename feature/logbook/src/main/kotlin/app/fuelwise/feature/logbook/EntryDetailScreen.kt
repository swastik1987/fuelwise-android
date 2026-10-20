package app.fuelwise.feature.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwInfoSheet
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.entry.EntryLabels
import app.fuelwise.core.ui.entry.categoryName
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.efficiencyText
import app.fuelwise.core.ui.price.fairPriceText
import app.fuelwise.core.ui.vehicle.VehicleLabels
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun EntryDetailScreen(
    onBack: () -> Unit,
    onEdit: (EntrySource, String) -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onBack() } }
    when (val s = state) {
        EntryDetailUiState.Loading -> Box(Modifier.fillMaxSize().background(FwTheme.colors.bg))

        EntryDetailUiState.Gone -> LaunchedEffect(Unit) { onBack() }

        is EntryDetailUiState.FillUp -> EntryDetailContent(s, onBack, {
            onEdit(EntrySource.Refuel, s.refuel.id)
        }, viewModel::delete)

        is EntryDetailUiState.Cost -> EntryDetailContent(s, onBack, {
            onEdit(EntrySource.Expense, s.expense.id)
        }, viewModel::delete)
    }
}

/** Entry detail (design.md screen 13): hero amount, key facts, the interval's mileage with its formula, notes. */
@Composable
internal fun EntryDetailContent(
    state: EntryDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var showFormula by rememberSaveable { mutableStateOf(false) }
    val title = when (state) {
        is EntryDetailUiState.FillUp -> stringResource(
            if (state.refuel.socEndPct !=
                null
            ) {
                R.string.detail_charge
            } else {
                R.string.detail_fill_up
            },
        )

        is EntryDetailUiState.Cost -> state.category?.let { categoryName(it) }
            ?: stringResource(R.string.detail_expense)

        else -> ""
    }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = title,
            onBack = onBack,
            backContentDescription = stringResource(R.string.detail_back),
            actions = {
                IconButton(onClick = onEdit) {
                    Icon(
                        painterResource(FwIcons.Edit),
                        stringResource(R.string.logbook_edit),
                        tint = FwTheme.colors.ink,
                    )
                }
                IconButton(onClick = { confirming = true }) {
                    Icon(
                        painterResource(FwIcons.Delete),
                        stringResource(R.string.logbook_delete),
                        tint = FwTheme.colors.ink,
                    )
                }
            },
        )
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
        ) {
            when (state) {
                is EntryDetailUiState.FillUp -> FillUpFacts(state) { showFormula = true }
                is EntryDetailUiState.Cost -> CostFacts(state)
                else -> Unit
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = FwTheme.colors.surface,
            title = { Text(stringResource(R.string.detail_delete_title), style = FwTheme.type.h3) },
            text = {
                Text(
                    stringResource(R.string.detail_delete_body),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.logbook_delete), color = FwTheme.colors.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirming = false
                }) { Text(stringResource(R.string.detail_cancel), color = FwTheme.colors.ink) }
            },
        )
    }
    if (showFormula && state is EntryDetailUiState.FillUp) {
        val interval = (state.result as? IntervalResult.Measured)?.interval
        val unit = state.vehicle?.tanks?.firstOrNull {
            it.id == state.refuel.tankId
        }?.let { stringResource(VehicleLabels.unit(it.energyType.canonicalUnit)) }.orEmpty()
        FwInfoSheet(
            title = stringResource(R.string.detail_formula_title),
            paragraphs = listOfNotNull(
                stringResource(R.string.detail_formula_body),
                interval?.let {
                    stringResource(
                        R.string.detail_formula_values,
                        IndianNumberFormat.decimal(it.distanceKm, 0),
                        IndianNumberFormat.decimal(it.quantity, 2),
                        unit,
                        IndianNumberFormat.decimal(it.kmPerUnit, 2),
                    )
                },
            ),
            onDismiss = { showFormula = false },
        )
    }
}

@Composable
private fun FillUpFacts(state: EntryDetailUiState.FillUp, onFormula: () -> Unit) {
    val r = state.refuel
    val tank = state.vehicle?.tanks?.firstOrNull { it.id == r.tankId }
    val unit = tank?.let { stringResource(VehicleLabels.unit(it.energyType.canonicalUnit)) }.orEmpty()
    Hero(
        IndianNumberFormat.rupees(r.paid),
        listOfNotNull(state.vehicle?.name, dateTime(r.dateTimeUtcMs, r.zoneId)).joinToString(" · "),
    )
    FwCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
        Fact(stringResource(R.string.detail_quantity), "${IndianNumberFormat.decimal(r.quantity, 2)} $unit")
        Fact(stringResource(R.string.detail_price, unit), IndianNumberFormat.rupees(r.unitPrice))
        state.fairPrice?.let { fair ->
            Text(
                fairPriceText(fair),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (r.discount.paise >
            0
        ) {
            Fact(stringResource(R.string.detail_discount), "−" + IndianNumberFormat.rupees(r.discount))
        }
        Fact(stringResource(R.string.detail_odometer), "${IndianNumberFormat.decimal(r.odometerKm, 0)} km")
        state.distanceSincePrevious?.let {
            Fact(stringResource(R.string.detail_since_last), "${IndianNumberFormat.decimal(it, 0)} km")
        }
        Fact(
            stringResource(R.string.detail_fill_type),
            stringResource(
                if (r.socEndPct !=
                    null
                ) {
                    R.string.detail_soc
                } else if (r.isFull) {
                    R.string.detail_full
                } else {
                    R.string.detail_partial
                },
                r.socStartPct?.toInt() ?: 0,
                r.socEndPct?.toInt() ?: 0,
            ),
        )
        r.stationName?.let { Fact(stringResource(R.string.detail_station), it) }
        r.paymentMethod?.let { Fact(stringResource(R.string.detail_payment), stringResource(EntryLabels.payment(it))) }
        r.grade?.let { Fact(stringResource(R.string.detail_grade), stringResource(EntryLabels.grade(it))) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FwOverline(stringResource(R.string.detail_this_interval))
        FwCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (val result = state.result) {
                        is IntervalResult.Measured -> efficiencyText(
                            result.interval.kmPerUnit,
                            tank?.energyType?.canonicalUnit ?: QuantityUnit.LITRE,
                            state.units,
                        )

                        IntervalResult.Baseline -> stringResource(R.string.detail_interval_baseline)

                        IntervalResult.Partial -> stringResource(R.string.detail_interval_partial)

                        is IntervalResult.Invalid -> stringResource(R.string.detail_interval_invalid)

                        null -> stringResource(R.string.detail_interval_partial)
                    },
                    style = FwTheme.type.h2,
                    color = FwTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onFormula) {
                    Icon(
                        painterResource(FwIcons.Info),
                        stringResource(R.string.detail_formula_title),
                        tint = FwTheme.colors.ink2,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    r.notes?.let { Notes(it) }
}

@Composable
private fun CostFacts(state: EntryDetailUiState.Cost) {
    val e = state.expense
    Hero(
        IndianNumberFormat.rupees(e.amount),
        listOfNotNull(state.vehicle?.name, dateTime(e.dateTimeUtcMs, e.zoneId)).joinToString(" · "),
    )
    FwCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
        state.category?.let { Fact(stringResource(R.string.detail_category), categoryName(it)) }
        e.tollPlaza?.let { Fact(stringResource(R.string.detail_plaza), it) }
        e.odometerKm?.let { Fact(stringResource(R.string.detail_odometer), "${IndianNumberFormat.decimal(it, 0)} km") }
        e.paymentMethod?.let { Fact(stringResource(R.string.detail_payment), stringResource(EntryLabels.payment(it))) }
    }
    e.notes?.let { Notes(it) }
}

@Composable
private fun Hero(amount: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(amount, style = FwTheme.type.display, color = FwTheme.colors.ink)
        Text(caption, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FwTheme.type.body, color = FwTheme.colors.ink2, modifier = Modifier.weight(1f))
        Text(value, style = FwTheme.type.label, color = FwTheme.colors.ink)
    }
    HorizontalDivider(color = FwTheme.colors.line)
}

@Composable
private fun Notes(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FwOverline(stringResource(R.string.detail_notes))
        Text(text, style = FwTheme.type.body, color = FwTheme.colors.ink)
    }
}

private fun dateTime(utcMs: Long, zoneId: String): String =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)
        .format(
            Instant.ofEpochMilli(utcMs).atZone(
                runCatching {
                    ZoneId.of(zoneId)
                }.getOrDefault(ZoneId.systemDefault()),
            ),
        )

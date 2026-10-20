package app.fuelwise.feature.prices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.prices.CityCatalog
import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.PriceFuel
import app.fuelwise.core.ui.entry.EntryDatePicker
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.price.PriceLabels
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Search field, popular cities as chips, then every match (design.md screen 3's picker). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityPickerSheet(
    catalog: CityCatalog,
    exclude: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        val matches = catalog.search(query).filterNot { it.id in exclude }
        LazyColumn(
            Modifier.padding(horizontal = FwTheme.spacing.gutter).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m)) {
                    Text(
                        stringResource(R.string.prices_picker_title),
                        style = FwTheme.type.h3,
                        color = FwTheme.colors.ink,
                    )
                    FwTextField(
                        value = query,
                        onValueChange = { query = it.take(MAX_QUERY) },
                        label = stringResource(R.string.prices_picker_search),
                    )
                    val popular = catalog.popular.filterNot { it.id in exclude }
                    if (query.isBlank() && popular.isNotEmpty()) {
                        FwOverline(stringResource(R.string.prices_picker_popular))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            popular.forEach { city -> FwChoiceChip(city.name, false, { onPick(city.id) }) }
                        }
                        FwOverline(stringResource(R.string.prices_picker_all), Modifier.padding(top = 8.dp))
                    }
                    if (matches.isEmpty()) {
                        Text(
                            stringResource(R.string.prices_picker_none),
                            style = FwTheme.type.bodyS,
                            color = FwTheme.colors.ink2,
                        )
                    }
                }
            }
            items(matches, key = { it.id }) { city ->
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(city.id) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(city.name, style = FwTheme.type.body, color = FwTheme.colors.ink)
                    Text(city.state, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
                }
            }
        }
    }
}

/** Type a city price for a day (PRD F-09.1): the board price, in ₹ per L or kg. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PriceEntrySheet(
    cityName: String,
    fuel: PriceFuel,
    today: LocalDate,
    existing: FuelPrice?,
    onSave: (LocalDate, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(existing?.price?.let(MoneyInput::plain).orEmpty()) }
    var dateEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpochDay)
    val unit = stringResource(PriceLabels.unit(fuel))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(
            Modifier.padding(horizontal = FwTheme.spacing.gutter).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m),
        ) {
            Text(
                stringResource(R.string.prices_sheet_title, stringResource(PriceLabels.fuel(fuel)), cityName),
                style = FwTheme.type.h3,
                color = FwTheme.colors.ink,
            )
            FwTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }.take(MAX_AMOUNT) },
                label = stringResource(R.string.prices_sheet_amount, unit),
                unit = "₹",
                keyboardType = KeyboardType.Decimal,
            )
            val dateText = IndianNumberFormat.date(date)
            val dateCd = stringResource(R.string.prices_sheet_date_cd, dateText)
            FwTextButton(
                stringResource(R.string.prices_sheet_date, dateText),
                { picking = true },
                Modifier.semantics { contentDescription = dateCd },
            )
            Text(stringResource(R.string.prices_sheet_hint), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
            FwInkButton(stringResource(R.string.prices_sheet_save), { onSave(date, amount) }, Modifier.fillMaxWidth())
            if (existing != null) {
                FwSecondaryButton(
                    stringResource(R.string.prices_sheet_delete),
                    { onDelete(existing.id) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (picking) {
        EntryDatePicker(
            initialUtcMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            zone = ZoneOffset.UTC,
            onPicked = { utcMs ->
                val picked = Instant.ofEpochMilli(utcMs).atZone(ZoneOffset.UTC).toLocalDate()
                // A price can't be set for a day that hasn't come yet.
                dateEpochDay = minOf(picked, today).toEpochDay()
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private const val MAX_QUERY = 40
private const val MAX_AMOUNT = 8

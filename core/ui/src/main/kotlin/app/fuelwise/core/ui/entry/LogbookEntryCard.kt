package app.fuelwise.core.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.component.FwEntryCard
import app.fuelwise.core.designsystem.component.FwEntryTile
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.designsystem.theme.FwTint
import app.fuelwise.core.domain.entry.EntryFacts
import app.fuelwise.core.domain.entry.MileageBand
import app.fuelwise.core.domain.entry.NoMileage
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.LogbookKind
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.R
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.UnitFormat
import app.fuelwise.core.ui.format.efficiencyUnit
import app.fuelwise.core.ui.format.efficiencyValue
import app.fuelwise.core.ui.vehicle.VehicleLabels
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A Logbook entry as the shared 88 dp card (design "Logbook entry card — one shell, two variants", 23 Nov 2026).
 * Fills and charges use the fuel variant, expenses and tolls the expense variant. [facts] come from
 * `core:domain` `LogbookFacts`; without them the tiles fall back to what the entry itself knows.
 */
@Composable
fun LogbookEntryCard(
    entry: LogbookEntry,
    vehicle: Vehicle?,
    categories: Map<String, Category>,
    facts: EntryFacts?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showVehicleName: Boolean = false,
    amountText: String = IndianNumberFormat.rupeesWhole(entry.amount),
    units: DisplayUnits = DisplayUnits.INDIA,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val date = cardDate(entry.dateTimeUtcMs, zone)
    val vehicleName = vehicle?.name?.takeIf { showVehicleName }
    val content = when (entry.kind) {
        LogbookKind.Fuel, LogbookKind.Charge -> fuelSlots(entry, vehicle, facts as? EntryFacts.Fill, units)
        LogbookKind.Expense, LogbookKind.Toll -> spendSlots(entry, categories, facts as? EntryFacts.Spend, units)
    }
    val subtitle = listOfNotNull(vehicleName, content.subtitle).joinToString(" · ")
    FwEntryCard(
        date = date,
        subtitle = subtitle.ifEmpty { null },
        secondary = content.secondary,
        amount = amountText,
        tiles = content.tiles,
        contentDescription = listOfNotNull(date, subtitle.ifEmpty { null }, amountText, content.secondary)
            .plus(content.spoken)
            .joinToString(", "),
        onClick = onClick,
        modifier = modifier,
    )
}

private class Slots(
    val subtitle: String?,
    val secondary: String?,
    val tiles: List<FwEntryTile>,
    /** What TalkBack reads for the tiles, with words instead of arrows. */
    val spoken: List<String>,
)

@Composable
private fun fuelSlots(entry: LogbookEntry, vehicle: Vehicle?, facts: EntryFacts.Fill?, units: DisplayUnits): Slots {
    val tank = vehicle?.tanks?.firstOrNull { it.id == entry.tankId }
    val energy = when {
        tank != null -> stringResource(VehicleLabels.energy(tank.energyType))
        entry.kind == LogbookKind.Charge -> stringResource(R.string.logbook_charge)
        else -> stringResource(R.string.logbook_fuel)
    }
    val unit = tank?.energyType?.canonicalUnit ?: facts?.unit ?: QuantityUnit.LITRE
    val distanceLabel = stringResource(UnitFormat.distance(units.distance))
    val quantityLabel = stringResource(UnitFormat.quantity(unit, units))
    val secondary = facts?.costPerKm?.let { perKm ->
        val perUnit = perKm.paise / units.distance.fromKm(1.0) / PAISE
        stringResource(R.string.card_cost_per_km, IndianNumberFormat.decimal(perUnit, 1), distanceLabel)
    }

    val mileage = mileageTile(facts, unit, units)
    val quantity = entry.quantity?.let { q ->
        FwEntryTile(
            value = IndianNumberFormat.decimal(units.quantity(q, unit), 1),
            unit = quantityLabel,
            caption = entry.unitPrice?.let { price ->
                val perShown = price.paise / units.quantity(1.0, unit) / PAISE
                stringResource(R.string.card_price_per_unit, IndianNumberFormat.decimal(perShown, 1), quantityLabel)
            },
        )
    } ?: FwEntryTile(value = "—", unit = quantityLabel)
    val odometer = entry.odometerKm?.let { odo ->
        FwEntryTile(
            value = IndianNumberFormat.decimal(units.distance.fromKm(odo), 0),
            unit = distanceLabel,
            caption = facts?.kmSinceLast?.let { km ->
                stringResource(
                    R.string.card_plus,
                    "${IndianNumberFormat.decimal(units.distance.fromKm(km), 0)} $distanceLabel",
                )
            },
        )
    } ?: FwEntryTile(value = "—", unit = distanceLabel)
    return Slots(
        subtitle = listOfNotNull(energy, entry.place?.takeIf { it.isNotBlank() }).joinToString(" · "),
        secondary = secondary,
        tiles = listOf(mileage.tile, quantity, odometer),
        spoken = listOf(mileage.spoken, quantity.spoken(), odometer.spoken()),
    )
}

private class Mileage(val tile: FwEntryTile, val spoken: String)

@Composable
private fun mileageTile(facts: EntryFacts.Fill?, unit: QuantityUnit, units: DisplayUnits): Mileage {
    val effUnit = efficiencyUnit(unit, units)
    val kmPerUnit = facts?.kmPerUnit
    if (kmPerUnit == null) {
        val label = stringResource(
            when (facts?.noMileage) {
                NoMileage.Partial -> R.string.card_partial
                NoMileage.Restarted -> R.string.card_restarted
                NoMileage.Baseline, null -> R.string.card_baseline
            },
        )
        val caption = stringResource(R.string.card_no_mileage_yet, effUnit)
        return Mileage(FwEntryTile(label, caption = caption, strongCaption = true), "$label, $caption")
    }
    val value = efficiencyValue(kmPerUnit, unit, units)
    val vsAverage = facts.vsAverage
    val pct = vsAverage?.let { (abs(it) * HUNDRED).roundToInt() } ?: 0
    val tints = FwTheme.colors.tints
    val (caption, spoken) = when {
        vsAverage == null || pct == 0 -> stringResource(R.string.card_at_avg).let { it to it }
        vsAverage > 0 -> stringResource(R.string.card_vs_avg_up, pct) to stringResource(R.string.card_cd_above, pct)
        else -> stringResource(R.string.card_vs_avg_down, pct) to stringResource(R.string.card_cd_below, pct)
    }
    val tint = when (facts.band) {
        MileageBand.Better -> tints.better
        MileageBand.Worse -> tints.worse
        MileageBand.Usual -> null
    }
    return Mileage(
        FwEntryTile(value, unit = effUnit, caption = caption, tint = tint, strongCaption = true),
        "$value $effUnit, $spoken",
    )
}

@Composable
private fun spendSlots(
    entry: LogbookEntry,
    categories: Map<String, Category>,
    facts: EntryFacts.Spend?,
    units: DisplayUnits,
): Slots {
    val category = entry.categoryId?.let { categories[it] }
    val name = when {
        category != null -> categoryName(category)
        entry.kind == LogbookKind.Toll -> stringResource(R.string.logbook_toll)
        else -> stringResource(R.string.category_other)
    }
    val distanceLabel = stringResource(UnitFormat.distance(units.distance))
    val categoryTile = FwEntryTile(
        value = name,
        icon = EntryLabels.categoryIcon(category),
        tint = categoryTint(category),
    )
    val odometer = entry.odometerKm?.let {
        FwEntryTile(
            value = IndianNumberFormat.decimal(units.distance.fromKm(it), 0),
            unit = distanceLabel,
            caption = stringResource(R.string.card_odometer),
        )
    } ?: FwEntryTile(stringResource(R.string.card_not_logged), caption = stringResource(R.string.card_tap_to_add))
    val sinceSame = sinceSameTile(facts, distanceLabel, units)
    return Slots(
        subtitle = listOfNotNull(name, entry.place?.takeIf { it.isNotBlank() }).joinToString(" · "),
        secondary = null,
        tiles = listOf(categoryTile, odometer, sinceSame),
        spoken = listOf(odometer.spoken(), sinceSame.spoken()),
    )
}

@Composable
private fun sinceSameTile(facts: EntryFacts.Spend?, distanceLabel: String, units: DisplayUnits): FwEntryTile {
    if (facts == null || !facts.hasEarlier) {
        return FwEntryTile(
            stringResource(R.string.card_first_log),
            caption = stringResource(R.string.card_none_earlier),
        )
    }
    val days = facts.daysSinceSame?.toInt()
    val ago = when {
        days == null -> null
        days == 0 -> stringResource(R.string.card_same_day)
        else -> pluralStringResource(R.plurals.card_days_ago, days, days)
    }
    val km = facts.kmSinceSame
    return if (km != null) {
        FwEntryTile(
            value = stringResource(R.string.card_plus, IndianNumberFormat.decimal(units.distance.fromKm(km), 0)),
            unit = distanceLabel,
            caption = ago,
        )
    } else {
        FwEntryTile(
            value = days?.let { pluralStringResource(R.plurals.card_days, it, it) } ?: "—",
            caption = stringResource(R.string.card_since_last),
        )
    }
}

/**
 * Category tints (owner choice 23 Nov 2026, grouped by type). Never green or red, so a tinted tile in the All list
 * can't be read as good or bad mileage. Custom categories and tolls are grey.
 */
@Composable
fun categoryTint(category: Category?): FwTint {
    val tints = FwTheme.colors.tints
    return when (category?.systemKey) {
        SystemCategory.Service, SystemCategory.Repair, SystemCategory.Tyres, SystemCategory.Wash,
        SystemCategory.Accessories, SystemCategory.ExtendedWarranty,
        -> tints.upkeep

        SystemCategory.Battery -> tints.battery

        SystemCategory.Purchase, SystemCategory.LoanEmi, SystemCategory.Insurance, SystemCategory.RoadTax,
        SystemCategory.Registration, SystemCategory.Puc, SystemCategory.Fine,
        -> tints.paperwork

        SystemCategory.Parking, SystemCategory.TollFastag, SystemCategory.TollCash, SystemCategory.Other, null,
        -> tints.other
    }
}

private fun FwEntryTile.spoken(): String =
    listOfNotNull(listOfNotNull(value, unit).joinToString(" "), caption).joinToString(", ")

/** "Mon, 23 Nov". The month header carries the year. */
fun cardDate(utcMs: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.ENGLISH): String =
    DateTimeFormatter.ofPattern("EEE, d MMM", locale).format(Instant.ofEpochMilli(utcMs).atZone(zone))

private const val PAISE = 100.0
private const val HUNDRED = 100

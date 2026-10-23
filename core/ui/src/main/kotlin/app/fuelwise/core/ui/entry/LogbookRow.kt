package app.fuelwise.core.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.component.FwEntryRow
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.EnergyColors
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.LogbookKind
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.R
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.distanceText
import app.fuelwise.core.ui.format.efficiencyText
import app.fuelwise.core.ui.format.quantityText
import app.fuelwise.core.ui.vehicle.VehicleLabels
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One Logbook/Recent row (docs/design.md §5 "Entry row"): "Petrol · 32.4 L" over "14 Oct · Shell, HSR Layout ·
 * 42,318 km", the amount on the right and the interval's km/L chip on closing fills.
 */
@Composable
fun LogbookEntryRow(
    entry: LogbookEntry,
    vehicle: Vehicle?,
    categories: Map<String, Category>,
    kmPerUnit: Double?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showVehicleName: Boolean = false,
    amountText: String = IndianNumberFormat.rupeesWhole(entry.amount),
    /** The vehicle's display units (PRD F-18); storage stays canonical. */
    units: DisplayUnits = DisplayUnits.INDIA,
) {
    val tank = vehicle?.tanks?.firstOrNull { it.id == entry.tankId }
    val quantityUnit = tank?.energyType?.canonicalUnit
    val quantity = entry.quantity?.let { q ->
        quantityUnit?.let { quantityText(q, it, units) } ?: IndianNumberFormat.decimal(q, 1)
    }
    val category = entry.categoryId?.let { categories[it] }
    val title = when (entry.kind) {
        LogbookKind.Fuel -> listOfNotNull(
            tank?.let { stringResource(VehicleLabels.energy(it.energyType)) } ?: stringResource(R.string.logbook_fuel),
            quantity,
        ).joinToString(" · ")

        LogbookKind.Charge -> listOfNotNull(
            stringResource(R.string.logbook_charge),
            quantity,
        ).joinToString(" · ")

        LogbookKind.Toll -> listOfNotNull(stringResource(R.string.logbook_toll), entry.place).joinToString(" · ")

        LogbookKind.Expense -> category?.let { categoryName(it) } ?: stringResource(R.string.category_other)
    }
    val meta = listOfNotNull(
        shortDate(entry.dateTimeUtcMs),
        if (showVehicleName) vehicle?.name else null,
        entry.place.takeIf { entry.kind == LogbookKind.Fuel || entry.kind == LogbookKind.Charge },
        entry.odometerKm?.let { distanceText(it, units) },
    ).joinToString(" · ")
    val (icon, tint) = when (entry.kind) {
        LogbookKind.Fuel ->
            FwIcons.Fuel to
                (tank?.let { VehicleLabels.energyColor(it.energyType) } ?: EnergyColors.Petrol)

        LogbookKind.Charge -> FwIcons.Charge to EnergyColors.Electric

        LogbookKind.Toll -> FwIcons.Toll to EnergyColors.Premium

        LogbookKind.Expense -> EntryLabels.categoryIcon(category) to FwTheme.colors.ink2
    }
    FwEntryRow(
        icon = icon,
        tint = tint,
        title = title,
        meta = meta,
        amount = amountText,
        chip = kmPerUnit?.let { km -> quantityUnit?.let { efficiencyText(km, it, units) } },
        onClick = onClick,
        modifier = modifier,
    )
}

/** "14 Oct" in the device zone. */
fun shortDate(utcMs: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.ENGLISH): String =
    DateTimeFormatter.ofPattern("d MMM", locale).format(Instant.ofEpochMilli(utcMs).atZone(zone))

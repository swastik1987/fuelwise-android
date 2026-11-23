package app.fuelwise.feature.logbook

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.domain.entry.MonthSummary
import app.fuelwise.core.ui.entry.categoryName
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.UnitFormat
import app.fuelwise.core.ui.format.efficiencyText
import app.fuelwise.core.ui.format.quantityText

/**
 * The month header's right side (design "Month header by filter", 23 Nov 2026): counts for All, "62.4 L · 509 km ·
 * 8.2 km/L" for Fuel or Charge, category names for Expenses or Tolls.
 */
@Composable
internal fun monthSummaryText(summary: MonthSummary, chrome: LogbookChrome): String {
    // Figures for one vehicle use its units; "All vehicles" uses the app's.
    val units = chrome.selectedVehicle?.takeUnless { chrome.allVehicles }?.let { chrome.units.forVehicle(it.id) }
        ?: chrome.units.forVehicle(null)
    return when (summary) {
        is MonthSummary.Counts -> listOfNotNull(
            summary.fills.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.logbook_summary_fills, it, it) },
            summary.charges.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.logbook_summary_charges, it, it) },
            summary.expenses.takeIf { it > 0 }?.let {
                pluralStringResource(R.plurals.logbook_summary_expenses, it, it)
            },
            summary.tolls.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.logbook_summary_tolls, it, it) },
        ).joinToString(" · ")

        is MonthSummary.Energy -> {
            val distance = "${IndianNumberFormat.decimal(units.distance.fromKm(summary.km), 0)} " +
                stringResource(UnitFormat.distance(units.distance))
            listOfNotNull(
                summary.unit?.let { unit -> summary.quantity?.let { quantityText(it, unit, units) } },
                distance.takeIf { summary.km > 0 },
                summary.unit?.let { unit -> summary.kmPerUnit?.let { efficiencyText(it, unit, units) } },
            ).joinToString(" · ")
        }

        is MonthSummary.Categories -> {
            val names = summary.categoryIds.map { id ->
                chrome.categories[id]?.let { categoryName(it) } ?: stringResource(R.string.logbook_summary_other)
            }.distinct()
            val shown = names.take(MAX_NAMES).joinToString(", ")
            if (names.size > MAX_NAMES) {
                stringResource(R.string.logbook_summary_more, shown, names.size - MAX_NAMES)
            } else {
                shown
            }
        }
    }
}

private const val MAX_NAMES = 3

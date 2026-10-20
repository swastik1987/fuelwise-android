package app.fuelwise.feature.dataimport

import android.content.res.Resources
import app.fuelwise.core.domain.export.CsvValues
import app.fuelwise.core.domain.export.CsvWriter
import app.fuelwise.core.domain.report.BusinessMileageReport
import app.fuelwise.core.domain.report.ReportPeriod
import app.fuelwise.core.domain.report.SellerReport
import app.fuelwise.core.domain.report.VehicleReport
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.R as UiR
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleLabels
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A built report, whichever kind. */
sealed interface Report {
    data class Vehicle(val report: VehicleReport) : Report

    data class Seller(val report: SellerReport) : Report

    data class Business(val report: BusinessMileageReport) : Report
}

/**
 * Renders the free reports (PRD F-13.3) as shareable text and as CSV, in the app's language. Money goes through
 * [IndianNumberFormat] in text and through [CsvValues] (plain rupees) in CSV. A PDF renderer (Pro) will sit beside
 * this one and take the same [Report].
 */
@Suppress("TooManyFunctions") // Text and CSV renderers for each of the three reports.
internal class ReportFormatter(
    private val resources: Resources,
    private val vehicles: List<Vehicle>,
    private val categories: List<Category>,
    private val locale: Locale,
) {
    private fun s(id: Int, vararg args: Any): String = resources.getString(id, *args)

    private fun vehicleName(id: String) = vehicles.firstOrNull { it.id == id }?.name.orEmpty()

    private fun categoryName(id: String) = categories.firstOrNull { it.id == id }
        ?.let { ImportTexts.categoryName(resources, it) }
        ?: s(R.string.report_category_unknown)

    private fun date(day: LocalDate) = IndianNumberFormat.date(day, locale)

    private fun km(value: Double) = s(R.string.report_km, IndianNumberFormat.decimal(value, 0))

    private fun unit(type: EnergyType) = s(VehicleLabels.unit(type.canonicalUnit))

    fun period(period: ReportPeriod): String = when {
        period.start.dayOfMonth == 1 && period.endExclusive == period.start.plusMonths(1) ->
            DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(period.start)

        period.start.dayOfYear == 1 && period.endExclusive == period.start.plusYears(1) -> period.start.year.toString()

        else -> s(R.string.report_period_range, date(period.start), date(period.end))
    }

    private fun heading(vehicle: Vehicle) = listOfNotNull(vehicle.name, vehicle.registrationNumber).joinToString(" · ")

    fun title(report: Report): String = when (report) {
        is Report.Vehicle -> s(R.string.report_vehicle_title)
        is Report.Seller -> s(R.string.report_seller_title)
        is Report.Business -> s(R.string.report_business_title)
    }

    fun text(report: Report): String = when (report) {
        is Report.Vehicle -> vehicleText(report.report)
        is Report.Seller -> sellerText(report.report)
        is Report.Business -> businessText(report.report)
    }

    fun csv(report: Report): String = when (report) {
        is Report.Vehicle -> vehicleCsv(report.report)
        is Report.Seller -> sellerCsv(report.report)
        is Report.Business -> businessCsv(report.report)
    }

    private fun vehicleText(r: VehicleReport): String = buildString {
        appendLine(s(R.string.report_header, s(R.string.report_vehicle_title)))
        appendLine(heading(r.vehicle))
        appendLine(period(r.period))
        appendLine()
        if (r.isEmpty) {
            appendLine(s(R.string.report_vehicle_empty))
            return@buildString
        }
        r.distance?.let {
            appendLine(s(if (it.isEstimated) R.string.report_distance_est else R.string.report_distance, km(it.km)))
        }
        r.energy.forEach { e ->
            appendLine(
                s(
                    R.string.report_energy_line,
                    s(VehicleLabels.energy(e.energyType)),
                    resources.getQuantityString(R.plurals.report_fill_ups, e.fillUps, e.fillUps),
                    IndianNumberFormat.decimal(e.quantity, 1) + " " + unit(e.energyType),
                    IndianNumberFormat.rupees(e.paid),
                ),
            )
        }
        if (r.categories.isNotEmpty()) {
            appendLine(s(R.string.report_expenses, IndianNumberFormat.rupees(r.expenseSpend)))
            r.categories.forEach { c ->
                appendLine(
                    "  " + s(
                        R.string.report_expense_line,
                        categoryName(c.categoryId),
                        c.entries,
                        IndianNumberFormat.rupees(c.amount),
                    ),
                )
            }
        }
        appendLine(s(R.string.report_total, IndianNumberFormat.rupees(r.totalSpend)))
        r.costPerKm?.let { appendLine(s(R.string.report_cost_per_km, IndianNumberFormat.rupees(it))) }
        val average = r.average
        val energy = r.averageEnergy
        if (average != null && energy != null) {
            appendLine(s(R.string.report_average, IndianNumberFormat.decimal(average.kmPerUnit, 1), unit(energy)))
        }
    }.trimEnd()

    private fun sellerText(r: SellerReport): String = buildString {
        appendLine(s(R.string.report_header, s(R.string.report_seller_title)))
        appendLine(heading(r.vehicle))
        listOfNotNull(r.vehicle.make, r.vehicle.model, r.vehicle.variant, r.vehicle.year?.toString())
            .takeIf { it.isNotEmpty() }?.let { appendLine(it.joinToString(" ")) }
        appendLine()
        appendLine(s(R.string.report_odometer, km(r.odometerKm)))
        r.since?.let { appendLine(s(R.string.report_logged_since, date(it), km(r.distanceLoggedKm))) }
        appendLine(resources.getQuantityString(R.plurals.report_fill_ups_logged, r.fillUps, r.fillUps))
        val average = r.average
        val energy = r.averageEnergy
        if (average != null && energy != null) {
            appendLine(s(R.string.report_average, IndianNumberFormat.decimal(average.kmPerUnit, 1), unit(energy)))
        }
        appendLine()
        appendLine(s(R.string.report_service_history))
        if (r.service.isEmpty()) appendLine("  " + s(R.string.report_none))
        r.service.forEach { line ->
            appendLine(
                "  " + listOfNotNull(date(line.date), line.odometerKm?.let(::km), categoryName(line.categoryId))
                    .joinToString(" · "),
            )
        }
        appendLine()
        appendLine(s(R.string.report_documents))
        if (r.documents.isEmpty()) appendLine("  " + s(R.string.report_none))
        r.documents.forEach { d ->
            val type = s(CareLabels.document(d.type))
            appendLine("  " + (d.expiry?.let { s(R.string.report_valid_until, type, date(it)) } ?: type))
        }
        appendLine()
        append(s(R.string.report_prices_hidden))
    }

    private fun businessText(r: BusinessMileageReport): String = buildString {
        appendLine(s(R.string.report_header, s(R.string.report_business_title)))
        appendLine(period(r.period))
        appendLine(s(R.string.report_rate, IndianNumberFormat.rupees(r.ratePerKm)))
        appendLine()
        if (r.trips.isEmpty()) {
            appendLine(s(R.string.report_business_empty))
            return@buildString
        }
        r.trips.forEach { t ->
            val route = listOfNotNull(t.fromPlace, t.toPlace).joinToString(" → ")
            appendLine(
                listOf(date(t.date), vehicleName(t.vehicleId), route.ifEmpty { null }, km(t.distanceKm))
                    .filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ") +
                    " · " + IndianNumberFormat.rupees(t.amount),
            )
        }
        appendLine()
        appendLine(resources.getQuantityString(R.plurals.report_business_trips, r.trips.size, r.trips.size))
        appendLine(s(R.string.report_total_distance, km(r.totalKm)))
        append(s(R.string.report_total_claim, IndianNumberFormat.rupees(r.totalAmount)))
    }

    private fun vehicleCsv(r: VehicleReport): String {
        val rows = buildList {
            r.distance?.let {
                add(
                    listOf(
                        s(R.string.report_csv_distance),
                        null,
                        CsvValues.decimal(it.km),
                        s(UiR.string.unit_km),
                        null,
                    ),
                )
            }
            r.energy.forEach { e ->
                add(
                    listOf(
                        s(VehicleLabels.energy(e.energyType)),
                        e.fillUps.toString(),
                        CsvValues.decimal(e.quantity, QUANTITY_DECIMALS),
                        unit(e.energyType),
                        CsvValues.rupees(e.paid.paise),
                    ),
                )
            }
            r.categories.forEach { c ->
                add(
                    listOf(
                        CsvValues.text(categoryName(c.categoryId)),
                        c.entries.toString(),
                        null,
                        null,
                        CsvValues.rupees(c.amount.paise),
                    ),
                )
            }
            add(listOf(s(R.string.report_csv_total), null, null, null, CsvValues.rupees(r.totalSpend.paise)))
            r.costPerKm?.let {
                add(listOf(s(R.string.report_csv_cost_per_km), null, null, null, CsvValues.rupees(it.paise)))
            }
        }
        return CsvWriter.write(
            listOf(
                s(R.string.report_csv_item),
                s(R.string.report_csv_count),
                s(R.string.report_csv_quantity),
                s(R.string.report_csv_unit),
                s(R.string.report_csv_amount),
            ),
            rows,
        )
    }

    private fun sellerCsv(r: SellerReport): String {
        val service = s(R.string.report_csv_service)
        val document = s(R.string.report_csv_document)
        val rows = buildList {
            add(
                listOf(
                    s(R.string.report_csv_odometer),
                    null,
                    CsvValues.decimal(r.odometerKm),
                    CsvValues.text(r.vehicle.name),
                ),
            )
            r.service.forEach {
                add(
                    listOf(
                        service,
                        it.date.toString(),
                        it.odometerKm?.let(CsvValues::decimal),
                        CsvValues.text(categoryName(it.categoryId)),
                    ),
                )
            }
            r.documents.forEach { add(listOf(document, it.expiry?.toString(), null, s(CareLabels.document(it.type)))) }
        }
        return CsvWriter.write(
            listOf(
                s(R.string.report_csv_section),
                s(R.string.report_csv_date),
                s(R.string.report_csv_odometer_km),
                s(R.string.report_csv_item),
            ),
            rows,
        )
    }

    private fun businessCsv(r: BusinessMileageReport): String {
        val rate = CsvValues.rupees(r.ratePerKm.paise)
        val rows = r.trips.map { t ->
            listOf(
                t.date.toString(),
                CsvValues.text(vehicleName(t.vehicleId)),
                CsvValues.text(t.fromPlace),
                CsvValues.text(t.toPlace),
                CsvValues.text(t.notes),
                CsvValues.decimal(t.distanceKm),
                rate,
                CsvValues.rupees(t.amount.paise),
            )
        } + listOf(
            listOf(
                s(R.string.report_csv_total),
                null,
                null,
                null,
                null,
                CsvValues.decimal(r.totalKm),
                rate,
                CsvValues.rupees(r.totalAmount.paise),
            ),
        )
        return CsvWriter.write(
            listOf(
                s(R.string.report_csv_date),
                s(R.string.report_csv_vehicle),
                s(R.string.report_csv_from),
                s(R.string.report_csv_to),
                s(R.string.report_csv_notes),
                s(R.string.report_csv_distance_km),
                s(R.string.report_csv_rate),
                s(R.string.report_csv_amount),
            ),
            rows,
        )
    }
}

private const val QUANTITY_DECIMALS = 3

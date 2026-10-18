package app.fuelwise.core.domain.importing

import app.fuelwise.core.domain.entry.PriceTriangle
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToLong

/** A row in the file: the sheet (null for CSV) and its 1-based spreadsheet row number. */
data class RowRef(val sheet: String?, val row: Int)

/** Why a row can't be imported. The preview lists each with its row number and the offending value. */
enum class RowIssue {
    VehicleNotFound,
    NoVehicle,
    MissingDate,
    BadDate,
    MissingOdometer,
    BadNumber,
    MissingFuelAmounts,
    FuelNotOnVehicle,
    MissingAmount,
    IncomeNotSupported,
    TooManyRows,
}

data class RowProblem(val at: RowRef, val issue: RowIssue, val value: String? = null)

/** What the plan is checked against: the user's vehicles and entries, and how to read local dates. */
data class ImportContext(
    val vehicles: List<Vehicle>,
    /** Rows with no vehicle column (or a blank cell) go to this vehicle. */
    val defaultVehicleId: String?,
    val existingRefuels: List<Refuel>,
    val existingExpenses: List<Expense>,
    val vocabulary: ImportVocabulary,
    val zone: ZoneId,
    val dateOrder: DateOrder = DateOrder.DayMonthYear,
)

/**
 * The dry run (PRD F-13.2): entries ready to save (ids blank), duplicates skipped, and problems by row.
 * Nothing is written until the user confirms.
 */
data class ImportPlan(
    val refuels: List<Refuel>,
    val expenses: List<Expense>,
    val duplicates: Int,
    val problems: List<RowProblem>,
    /** Expenses whose category wasn't recognised: filed under Other with the original name in the notes. */
    val otherCategoryRows: Int,
) {
    val entryCount: Int get() = refuels.size + expenses.size
}

object ImportPlanner {
    const val MAX_ROWS = 10_000

    fun plan(
        tables: List<MappedTable>,
        context: ImportContext,
        fileProblems: List<RowProblem> = emptyList(),
    ): ImportPlan {
        val rows = tables.sumOf { t -> t.table.rows.count { r -> r.any { !it.isBlank } } }
        if (rows > MAX_ROWS) {
            return ImportPlan(emptyList(), emptyList(), 0, listOf(RowProblem(RowRef(null, 0), RowIssue.TooManyRows)), 0)
        }
        val state = PlanState(context)
        tables.forEach { mapped ->
            mapped.table.rows.forEachIndexed { i, row ->
                if (row.all { it.isBlank }) return@forEachIndexed
                val reader = RowReader(mapped, row, RowRef(mapped.table.sheetName, mapped.table.rowNumber(i)), context)
                when (mapped.mapping.kind) {
                    EntryKind.Fuel -> state.addRefuel(reader.refuel())
                    EntryKind.Expense -> state.addExpense(reader.expense())
                }
            }
        }
        return ImportPlan(
            refuels = state.refuels,
            expenses = state.expenses,
            duplicates = state.duplicates,
            problems = fileProblems + state.problems,
            otherCategoryRows = state.otherCategory,
        )
    }

    /** True when some row names no vehicle, so the preview has to ask which vehicle they belong to. */
    fun usesDefaultVehicle(tables: List<MappedTable>): Boolean = tables.any { t ->
        val column = t.mapping[ImportField.Vehicle]
        t.table.rows.any { row ->
            row.any { !it.isBlank } && (column == null || row.getOrNull(column)?.isBlank != false)
        }
    }

    /** The date cells of every table, for [ImportValues.detectDateOrder]. */
    fun dateCells(tables: List<MappedTable>): List<Cell> = tables.flatMap { t ->
        val column = t.mapping[ImportField.Date] ?: return@flatMap emptyList()
        t.table.rows.mapNotNull { it.getOrNull(column) }
    }

    /** Rows accepted so far plus duplicate keys, so a row repeated within the file is also caught. */
    private class PlanState(private val context: ImportContext) {
        val refuels = mutableListOf<Refuel>()
        val expenses = mutableListOf<Expense>()
        val problems = mutableListOf<RowProblem>()
        var duplicates = 0
        var otherCategory = 0
        private val refuelKeys = context.existingRefuels.mapTo(mutableSetOf()) { key(it) }
        private val expenseKeys = context.existingExpenses.mapTo(mutableSetOf()) { key(it) }

        fun addRefuel(result: RowResult<Refuel>) {
            val refuel = result.value ?: return problems.plusAssign(result.problem!!)
            if (!refuelKeys.add(key(refuel))) duplicates++ else refuels += refuel
        }

        fun addExpense(result: RowResult<Expense>) {
            val expense = result.value ?: return problems.plusAssign(result.problem!!)
            if (!expenseKeys.add(key(expense))) {
                duplicates++
                return
            }
            expenses += expense
            if (result.otherCategory) otherCategory++
        }

        private fun day(utcMs: Long, zoneId: String) = Instant.ofEpochMilli(utcMs).atZone(
            runCatching {
                ZoneId.of(zoneId)
            }.getOrDefault(context.zone),
        ).toLocalDate()

        /** Same vehicle, day, odometer to the km and quantity to 0.01: the same fill-up. */
        private fun key(r: Refuel) = "${r.vehicleId}|${day(
            r.dateTimeUtcMs,
            r.zoneId,
        )}|${r.odometerKm.roundToLong()}|${(r.quantity * CENTS).roundToLong()}"

        /** Same vehicle, day, category and amount: the same expense. */
        private fun key(e: Expense) =
            "${e.vehicleId}|${day(e.dateTimeUtcMs, e.zoneId)}|${e.categoryId}|${e.amount.paise}"
    }

    private class RowResult<T>(val value: T?, val problem: RowProblem? = null, val otherCategory: Boolean = false)

    /** Reads one row through its table's mapping. Each read returns a value or the problem that stopped it. */
    private class RowReader(
        private val mapped: MappedTable,
        private val row: List<Cell>,
        private val at: RowRef,
        private val context: ImportContext,
    ) {
        private val vocabulary = context.vocabulary

        private fun cell(field: ImportField): Cell = mapped.mapping[field]?.let { row.getOrNull(it) } ?: Cell.Empty

        private fun <T> fail(issue: RowIssue, cell: Cell? = null) = RowResult<T>(
            null,
            RowProblem(
                at,
                issue,
                cell?.asText()?.ifEmpty {
                    null
                },
            ),
        )

        fun refuel(): RowResult<Refuel> {
            val vehicle = vehicle() ?: return fail(vehicleIssue(), cell(ImportField.Vehicle))
            val time = dateTime() ?: return fail(dateIssue(), cell(ImportField.Date))
            val odometer =
                odometer()
                    ?: return fail(
                        numberIssue(ImportField.Odometer, RowIssue.MissingOdometer),
                        cell(ImportField.Odometer),
                    )
            val tank = tank(vehicle) ?: return fail(RowIssue.FuelNotOnVehicle, tankCell())
            for (field in listOf(ImportField.Quantity, ImportField.UnitPrice, ImportField.Total)) {
                if (!cell(field).isBlank &&
                    ImportValues.number(cell(field)) == null
                ) {
                    return fail(RowIssue.BadNumber, cell(field))
                }
            }
            val amounts = amounts(tank) ?: return fail(RowIssue.MissingFuelAmounts)
            return RowResult(
                Refuel(
                    id = "",
                    vehicleId = vehicle.id,
                    tankId = tank.id,
                    dateTimeUtcMs = time,
                    zoneId = context.zone.id,
                    odometerKm = odometer,
                    quantity = amounts.quantity!!,
                    unitPrice = amounts.unitPrice!!,
                    total = amounts.total!!,
                    isFull = ImportValues.flag(cell(ImportField.FullTank), vocabulary) ?: true,
                    missedPrevious = ImportValues.flag(cell(ImportField.MissedPrevious), vocabulary) ?: false,
                    stationName = text(ImportField.Station),
                    paymentMethod = text(ImportField.Payment)?.let(vocabulary::payment),
                    notes = text(ImportField.Notes),
                ),
            )
        }

        fun expense(): RowResult<Expense> {
            val vehicle = vehicle() ?: return fail(vehicleIssue(), cell(ImportField.Vehicle))
            val time = dateTime() ?: return fail(dateIssue(), cell(ImportField.Date))
            val amount = ImportValues.money(cell(ImportField.Amount))?.takeIf { it.paise > 0 }
                ?: return fail(numberIssue(ImportField.Amount, RowIssue.MissingAmount), cell(ImportField.Amount))
            val categoryText = text(ImportField.Category)
            val categoryId = categoryText?.let(vocabulary::category)
            val other = categoryText != null && categoryId == null
            val notes = listOfNotNull(categoryText.takeIf { other }, text(ImportField.Notes)).joinToString(" · ")
            return RowResult(
                Expense(
                    id = "",
                    vehicleId = vehicle.id,
                    categoryId = categoryId ?: SystemCategory.Other.id,
                    dateTimeUtcMs = time,
                    zoneId = context.zone.id,
                    amount = amount,
                    odometerKm = odometer(),
                    paymentMethod = text(ImportField.Payment)?.let(vocabulary::payment),
                    notes = notes.ifEmpty { null },
                    tollPlaza = text(ImportField.TollPlaza),
                ),
                otherCategory = other,
            )
        }

        private fun text(field: ImportField): String? = cell(field).asText().ifEmpty { null }

        /** The named vehicle (by name or registration), else the default one when the cell is blank. */
        private fun vehicle(): Vehicle? {
            val name =
                text(ImportField.Vehicle) ?: return context.vehicles.firstOrNull { it.id == context.defaultVehicleId }
            val key = ImportVocabulary.normalize(name)
            return context.vehicles.firstOrNull { ImportVocabulary.normalize(it.name) == key }
                ?: context.vehicles.firstOrNull { v -> v.registrationNumber?.let(ImportVocabulary::normalize) == key }
        }

        private fun vehicleIssue() = if (text(ImportField.Vehicle) ==
            null
        ) {
            RowIssue.NoVehicle
        } else {
            RowIssue.VehicleNotFound
        }

        private fun dateIssue() = if (cell(ImportField.Date).isBlank) RowIssue.MissingDate else RowIssue.BadDate

        private fun numberIssue(field: ImportField, missing: RowIssue) =
            if (cell(field).isBlank) missing else RowIssue.BadNumber

        /** Date and time as UTC ms in the device zone. A date without a time is taken as noon. */
        private fun dateTime(): Long? {
            val parsed = ImportValues.dateTime(cell(ImportField.Date), context.dateOrder) ?: return null
            val time = ImportValues.time(cell(ImportField.Time))
                ?: parsed.toLocalTime().takeIf { it != LocalTime.MIDNIGHT }
                ?: NOON
            return LocalDateTime.of(parsed.toLocalDate(), time).atZone(context.zone).toInstant().toEpochMilli()
        }

        private fun odometer(): Double? =
            ImportValues.double(cell(ImportField.Odometer))?.takeIf { it > 0 }?.let { it * mapped.kmPerDistanceUnit }

        private fun tankCell() =
            cell(if (mapped.mapping[ImportField.TankNumber] != null) ImportField.TankNumber else ImportField.EnergyType)

        /** Tank number (1 = primary), else the named fuel, else the primary tank. */
        private fun tank(vehicle: Vehicle): Tank? {
            val ordered = vehicle.tanks.sortedByDescending { it.isPrimary }
            val number = text(ImportField.TankNumber)?.let { ImportValues.number(it)?.toInt() }
            val energyText = text(ImportField.EnergyType)
            return when {
                number != null -> ordered.getOrNull(number - 1)

                energyText == null -> vehicle.primaryTank

                else -> vocabulary.energy(energyText)?.let { energy ->
                    ordered.firstOrNull { it.energyType == energy }
                        ?: ordered.firstOrNull { it.energyType.isLiquid() && energy.isLiquid() && !vehicle.isBiFuel }
                }
            }
        }

        /** Two of quantity, price and total give the third (PRD F-02.2). Volumes in gallons become litres. */
        private fun amounts(tank: Tank): PriceTriangle.Values? {
            val litres = if (tank.energyType.canonicalUnit == QuantityUnit.LITRE) mapped.litresPerVolumeUnit else 1.0
            val quantity = ImportValues.double(cell(ImportField.Quantity))?.let { it * litres }?.takeIf { it > 0 }
            val unitPrice = ImportValues.number(cell(ImportField.UnitPrice))?.let { price ->
                Money(price.movePointRight(2).divide(litres.toBigDecimal(), 0, RoundingMode.HALF_UP).toLong())
            }
            val total = ImportValues.money(cell(ImportField.Total))
            val known = listOfNotNull(quantity, unitPrice, total).size
            if (known < 2) return null
            val values = PriceTriangle.Values(quantity, unitPrice, total)
            val solved = when {
                quantity == null -> PriceTriangle.solve(values, PriceTriangle.Field.Quantity)
                total == null -> PriceTriangle.solve(values, PriceTriangle.Field.Total)
                unitPrice == null -> PriceTriangle.solve(values, PriceTriangle.Field.UnitPrice)
                else -> values
            }
            val ok = (solved.quantity ?: 0.0) > 0 && solved.unitPrice != null && solved.total != null
            return solved.takeIf { ok }
        }

        private fun EnergyType.isLiquid() = canonicalUnit == QuantityUnit.LITRE
    }

    private val NOON: LocalTime = LocalTime.NOON
    private const val CENTS = 100
}

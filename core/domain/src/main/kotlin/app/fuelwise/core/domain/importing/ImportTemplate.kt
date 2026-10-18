package app.fuelwise.core.domain.importing

import app.fuelwise.core.domain.importing.xlsx.XlsxColumn
import app.fuelwise.core.domain.importing.xlsx.XlsxDropdown
import app.fuelwise.core.domain.importing.xlsx.XlsxSheet
import app.fuelwise.core.domain.importing.xlsx.XlsxStyle
import app.fuelwise.core.domain.importing.xlsx.XlsxValue
import app.fuelwise.core.domain.importing.xlsx.XlsxWriter

/**
 * The bulk-entry workbook (PRD F-13.2): a Fuel sheet and an Expenses sheet with dropdowns for vehicle, fuel,
 * category, payment and yes/no, a "How to fill" sheet, a hidden Lists sheet feeding the dropdowns, and a hidden
 * marker sheet that identifies the file when it comes back. Labels come from the app in the user's language.
 */
object ImportTemplate {
    const val MARKER = "fuelwise-template"
    const val VERSION = 1
    private const val MARKER_SHEET = "_fuelwise"
    private const val LISTS_SHEET = "Lists"

    val fuelColumns: List<ImportField> = listOf(
        ImportField.Vehicle, ImportField.Date, ImportField.Time, ImportField.Odometer, ImportField.EnergyType,
        ImportField.Quantity, ImportField.UnitPrice, ImportField.Total, ImportField.FullTank,
        ImportField.MissedPrevious, ImportField.Station, ImportField.Payment, ImportField.Notes,
    )

    val expenseColumns: List<ImportField> = listOf(
        ImportField.Vehicle,
        ImportField.Date,
        ImportField.Category,
        ImportField.Amount,
        ImportField.Odometer,
        ImportField.Payment,
        ImportField.TollPlaza,
        ImportField.Notes,
    )

    /** The fixed layout, used when a returned template's headers were edited beyond recognition. */
    fun positionalMapping(kind: EntryKind): ColumnMapping {
        val columns = if (kind == EntryKind.Fuel) fuelColumns else expenseColumns
        return ColumnMapping(kind, columns.withIndex().associate { it.value to it.index })
    }

    /** Everything the workbook says, in the user's language. */
    data class Labels(
        val fuelSheet: String,
        val expenseSheet: String,
        val howToSheet: String,
        val headers: Map<ImportField, String>,
        /** One explanation per column, shown on the How to fill sheet with an example value. */
        val help: Map<ImportField, String>,
        val examples: Map<ImportField, String>,
        val howToTitle: String,
        val howToIntro: List<String>,
        val howToColumns: Triple<String, String, String>,
    )

    data class Lists(
        val vehicles: List<String>,
        val energies: List<String>,
        val categories: List<String>,
        val payments: List<String>,
        val yesNo: List<String>,
    )

    fun write(labels: Labels, lists: Lists): ByteArray = XlsxWriter.write(sheets(labels, lists))

    internal fun sheets(labels: Labels, lists: Lists): List<XlsxSheet> {
        val listColumns = listOf(lists.vehicles, lists.energies, lists.categories, lists.payments, lists.yesNo)
        fun range(listIndex: Int): String {
            val column = XlsxWriter.columnName(listIndex)
            val size = listColumns[listIndex].size.coerceAtLeast(1)
            return "$LISTS_SHEET!\$$column\$1:\$$column\$$size"
        }
        val dropdownSource = mapOf(
            ImportField.Vehicle to range(VEHICLES),
            ImportField.EnergyType to range(ENERGIES),
            ImportField.Category to range(CATEGORIES),
            ImportField.Payment to range(PAYMENTS),
            ImportField.FullTank to range(YES_NO),
            ImportField.MissedPrevious to range(YES_NO),
        )
        fun dataSheet(name: String, fields: List<ImportField>) = XlsxSheet(
            name = name,
            columns = fields.map { XlsxColumn(labels.headers.getValue(it), width(it), style(it)) },
            dropdowns = fields.mapIndexedNotNull { i, f -> dropdownSource[f]?.let { XlsxDropdown(i, it) } },
        )
        val longest = listColumns.maxOf { it.size }
        val listRows = (0 until longest).map { r ->
            listColumns.map { values -> values.getOrNull(r)?.let(XlsxValue::Text) ?: XlsxValue.Empty }
        }
        return listOf(
            dataSheet(labels.fuelSheet, fuelColumns),
            dataSheet(labels.expenseSheet, expenseColumns),
            howTo(labels),
            XlsxSheet(LISTS_SHEET, emptyList(), listRows, hidden = true, editableRows = 0, hasHeader = false),
            XlsxSheet(
                MARKER_SHEET,
                emptyList(),
                listOf(listOf(XlsxValue.Text(MARKER), XlsxValue.Number(VERSION.toDouble()))),
                hidden = true,
                editableRows = 0,
                hasHeader = false,
            ),
        )
    }

    private fun howTo(labels: Labels): XlsxSheet {
        val (column, meaning, example) = labels.howToColumns
        fun section(title: String, fields: List<ImportField>) = listOf(listOf(XlsxValue.Text(title))) +
            fields.map { f ->
                listOf(
                    XlsxValue.Text(labels.headers.getValue(f)),
                    XlsxValue.Text(labels.help[f].orEmpty()),
                    XlsxValue.Text(labels.examples[f].orEmpty()),
                )
            }
        val rows = listOf(listOf(XlsxValue.Text(labels.howToTitle))) +
            labels.howToIntro.map { listOf(XlsxValue.Text(it)) } +
            listOf(emptyList<XlsxValue>()) +
            listOf(listOf(XlsxValue.Text(column), XlsxValue.Text(meaning), XlsxValue.Text(example))) +
            section(labels.fuelSheet, fuelColumns) +
            listOf(emptyList<XlsxValue>()) +
            section(labels.expenseSheet, expenseColumns)
        return XlsxSheet(
            labels.howToSheet,
            listOf(
                XlsxColumn("", HOW_TO_NAME_WIDTH, XlsxStyle.Wrap),
                XlsxColumn("", HOW_TO_TEXT_WIDTH, XlsxStyle.Wrap),
                XlsxColumn("", HOW_TO_EXAMPLE_WIDTH, XlsxStyle.Note),
            ),
            rows,
            editableRows = 0,
            hasHeader = false,
        )
    }

    /** True when [sheets] came from [write]: the hidden marker sheet is still there. */
    fun isTemplate(sheets: List<Sheet>): Boolean = sheets.any { sheet -> sheet.cell(0, 0).asText() == MARKER }

    private fun width(field: ImportField): Double = when (field) {
        ImportField.Vehicle, ImportField.Category, ImportField.Station, ImportField.TollPlaza -> WIDE
        ImportField.Notes -> NOTES
        else -> NARROW
    }

    private fun style(field: ImportField): XlsxStyle = when (field) {
        ImportField.Date -> XlsxStyle.Date
        ImportField.Time -> XlsxStyle.Time
        ImportField.Quantity -> XlsxStyle.Decimal
        ImportField.UnitPrice, ImportField.Total, ImportField.Amount -> XlsxStyle.Money
        else -> XlsxStyle.Default
    }

    private const val VEHICLES = 0
    private const val ENERGIES = 1
    private const val CATEGORIES = 2
    private const val PAYMENTS = 3
    private const val YES_NO = 4
    private const val NARROW = 14.0
    private const val WIDE = 22.0
    private const val NOTES = 32.0
    private const val HOW_TO_NAME_WIDTH = 24.0
    private const val HOW_TO_TEXT_WIDTH = 64.0
    private const val HOW_TO_EXAMPLE_WIDTH = 22.0
}

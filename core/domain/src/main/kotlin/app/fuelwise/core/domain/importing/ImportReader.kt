package app.fuelwise.core.domain.importing

import app.fuelwise.core.domain.importing.xlsx.XlsxReader
import app.fuelwise.core.domain.units.KM_PER_MILE
import app.fuelwise.core.domain.units.LITRES_PER_UK_GALLON
import app.fuelwise.core.domain.units.LITRES_PER_US_GALLON
import app.fuelwise.core.model.ImportSource

/**
 * A table with its mapping. Other apps may store miles and gallons: [kmPerDistanceUnit] and [litresPerVolumeUnit]
 * convert to canonical km and litres (CLAUDE.md rule 2).
 */
data class MappedTable(
    val table: RawTable,
    val mapping: ColumnMapping,
    val kmPerDistanceUnit: Double = 1.0,
    val litresPerVolumeUnit: Double = 1.0,
)

/**
 * A file, read and recognised. Template and Fuelio files arrive mapped in [tables]; any other spreadsheet comes as
 * [unmapped] for the column mapper. [vehicleHint] is the vehicle name the file carries (Fuelio), to preselect one.
 */
data class ImportFile(
    val source: ImportSource,
    val tables: List<MappedTable> = emptyList(),
    val unmapped: RawTable? = null,
    val vehicleHint: String? = null,
    val problems: List<RowProblem> = emptyList(),
)

/** Recognises the file type and turns it into tables (PRD F-13.2). Throws [IllegalArgumentException] if unreadable. */
object ImportReader {
    fun read(bytes: ByteArray, vocabulary: ImportVocabulary): ImportFile {
        if (XlsxReader.looksLikeXlsx(bytes)) return workbook(XlsxReader.read(bytes), vocabulary)
        val text = bytes.decodeToString()
        val sheet = Csv.parse(text)
        if (FuelioCsv.matches(sheet)) return FuelioCsv.read(sheet, vocabulary)
        val table = requireNotNull(RawTable.of(sheet)) { "The file is empty" }
        return ImportFile(ImportSource.Spreadsheet, unmapped = table)
    }

    private fun workbook(sheets: List<Sheet>, vocabulary: ImportVocabulary): ImportFile {
        val visible = sheets.filter { !it.hidden }
        if (!ImportTemplate.isTemplate(sheets)) {
            val table = visible.firstNotNullOfOrNull { RawTable.of(it)?.takeIf { t -> t.rows.isNotEmpty() } }
                ?: visible.firstNotNullOfOrNull(RawTable::of)
            return ImportFile(ImportSource.Spreadsheet, unmapped = requireNotNull(table) { "The file is empty" })
        }
        // The template's first two sheets are Fuel and Expenses, whatever they've been renamed to.
        val tables = listOf(EntryKind.Fuel, EntryKind.Expense).mapIndexedNotNull { i, kind ->
            val table = visible.getOrNull(i)?.let(RawTable::of) ?: return@mapIndexedNotNull null
            val suggested = ColumnMapping.suggest(table.headers, kind, vocabulary)
            val mapping = if (suggested.isComplete) suggested else ImportTemplate.positionalMapping(kind)
            MappedTable(table, mapping)
        }
        return ImportFile(ImportSource.Template, tables)
    }
}

/**
 * Fuelio's CSV export: sections that start with a "## Name" row, each with its own header. "## Vehicle" carries the
 * name and units, "## Log" the fill-ups, "## CostCategories" and "## Costs" the expenses. Columns are found by
 * header name, so added or reordered columns still import. Built from the documented layout without a real sample
 * (PRD D9); the generic mapper is the fallback for anything this misses.
 */
internal object FuelioCsv {
    private const val SECTION = "##"

    fun matches(sheet: Sheet): Boolean {
        val sections = sections(sheet).keys
        return "log" in sections && ("vehicle" in sections || "costs" in sections)
    }

    fun read(sheet: Sheet, vocabulary: ImportVocabulary): ImportFile {
        val sections = sections(sheet)
        val vehicle = sections["vehicle"]
        val units = Units.of(vehicle)
        val problems = mutableListOf<RowProblem>()
        val tables = listOfNotNull(
            sections["log"]?.let { log(it, units, vocabulary) },
            sections["costs"]?.let { costs(it, categoryNames(sections["costcategories"]), units, problems) },
        )
        val name = vehicle?.let { v ->
            v.headers.indexOfFirst { norm(it) == "name" }.takeIf { it >= 0 }?.let { v.samples(it, 1).firstOrNull() }
        }
        return ImportFile(ImportSource.Fuelio, tables, vehicleHint = name, problems = problems)
    }

    private fun log(table: RawTable, units: Units, vocabulary: ImportVocabulary): MappedTable {
        val suggested = ColumnMapping.suggest(table.headers, EntryKind.Fuel, vocabulary).columns.toMutableMap()
        // Fuelio's FuelType is a numeric code and its "Price" is the total; the tank number picks the tank.
        suggested.remove(ImportField.EnergyType)
        column(table, "volumeprice")?.let { suggested[ImportField.UnitPrice] = it }
        column(table, "price")?.let { suggested[ImportField.Total] = it }
        column(table, "tanknumber")?.let { suggested[ImportField.TankNumber] = it }
        return MappedTable(table, ColumnMapping(EntryKind.Fuel, suggested), units.kmPerUnit, units.litresPerUnit)
    }

    /** Rebuilds Costs as Date, Odometer, Category (by name), Amount, Notes; skips reminder templates and income. */
    private fun costs(
        table: RawTable,
        categories: Map<String, String>,
        units: Units,
        problems: MutableList<RowProblem>,
    ): MappedTable {
        val date = column(table, "date")
        val odo = column(table, "odo")
        val type = column(table, "costtypeid")
        val cost = column(table, "cost")
        val title = column(table, "costtitle")
        val notes = column(table, "notes")
        val template = column(table, "istemplate")
        val income = column(table, "isincome")
        val rows = mutableListOf<List<Cell>>()
        val rowNumbers = mutableListOf<Int>()
        table.rows.forEachIndexed { i, row ->
            fun at(c: Int?) = c?.let { row.getOrNull(it) } ?: Cell.Empty
            val number = table.rowNumber(i)
            if (at(template).asText() == "1") return@forEachIndexed
            if (at(income).asText() == "1") {
                problems += RowProblem(RowRef(table.sheetName, number), RowIssue.IncomeNotSupported)
                return@forEachIndexed
            }
            val category = categories[at(type).asText()] ?: at(type).asText()
            val text = listOf(at(title).asText(), at(notes).asText()).filter { it.isNotEmpty() }.distinct()
            rows += listOf(at(date), at(odo), Cell.Text(category), at(cost), Cell.Text(text.joinToString(" · ")))
            rowNumbers += number
        }
        val rebuilt =
            RawTable(table.sheetName, listOf("Date", "Odo", "Category", "Cost", "Notes"), rows, table.firstRowNumber)
        val order =
            listOf(ImportField.Date, ImportField.Odometer, ImportField.Category, ImportField.Amount, ImportField.Notes)
        val mapping = ColumnMapping(EntryKind.Expense, order.withIndex().associate { it.value to it.index })
        return MappedTable(rebuilt.copy(rowNumbers = rowNumbers), mapping, units.kmPerUnit)
    }

    private fun categoryNames(table: RawTable?): Map<String, String> {
        table ?: return emptyMap()
        val id = column(table, "costtypeid") ?: return emptyMap()
        val name = column(table, "name") ?: return emptyMap()
        return table.rows.associate { row ->
            row.getOrNull(id)?.asText().orEmpty() to
                row.getOrNull(name)?.asText().orEmpty()
        }
    }

    /** Fuelio stores units per vehicle: DistUnit 0 km / 1 miles; FuelUnit 0 litres / 1 US gal / 2 UK gal. */
    private data class Units(val kmPerUnit: Double, val litresPerUnit: Double) {
        companion object {
            fun of(vehicle: RawTable?): Units {
                vehicle ?: return Units(1.0, 1.0)
                val row = vehicle.rows.firstOrNull().orEmpty()
                fun value(header: String) = column(vehicle, header)?.let { row.getOrNull(it)?.asText() }
                val km = if (value("distunit") == "1") KM_PER_MILE else 1.0
                val litres = when (value("fuelunit")) {
                    "1" -> LITRES_PER_US_GALLON
                    "2" -> LITRES_PER_UK_GALLON
                    else -> 1.0
                }
                return Units(km, litres)
            }
        }
    }

    /** Sections keyed by lowercase name ("log", "costs"), each with its own header row. */
    private fun sections(sheet: Sheet): Map<String, RawTable> {
        val starts = sheet.rows.withIndex().filter { (_, row) ->
            row.firstOrNull()?.asText()?.startsWith(SECTION) ==
                true
        }
        return starts.mapIndexed { n, (index, row) ->
            val name = norm(row.first().asText().removePrefix(SECTION))
            val end = starts.getOrNull(n + 1)?.index ?: sheet.rows.size
            val body = (index + 1 until end).filter { r -> sheet.rows[r].any { !it.isBlank } }
            val header = body.firstOrNull()?.let { sheet.rows[it].map(Cell::asText) }.orEmpty()
            val data = body.drop(1)
            name to RawTable(name, header, data.map { sheet.rows[it] }, index + 1, rowNumbers = data.map { it + 1 })
        }.toMap()
    }

    /** First column whose normalized header is [name] or starts with it (Fuelio adds "(optional)" and units). */
    private fun column(table: RawTable, name: String): Int? {
        val headers = table.headers.map(::norm)
        return headers.indexOf(name).takeIf { it >= 0 }
            ?: headers.indexOfFirst { it.startsWith(name) }.takeIf { it >= 0 }
    }

    private fun norm(text: String) = ImportVocabulary.normalize(text)
}

package app.fuelwise.core.domain.importing

/** One spreadsheet cell as read from a file: CSV gives text only, .xlsx also gives numbers (dates are numbers). */
sealed interface Cell {
    data class Text(val value: String) : Cell

    data class Number(val value: Double) : Cell

    data object Empty : Cell

    val isBlank: Boolean get() = this is Empty || (this is Text && value.isBlank())

    /** The cell as text, for names, notes and the column mapper's samples. */
    fun asText(): String = when (this) {
        is Text -> value.trim()

        is Number -> if (value == Math.floor(value) &&
            !value.isInfinite()
        ) {
            value.toLong().toString()
        } else {
            value.toString()
        }

        Empty -> ""
    }
}

/** A sheet of a workbook, or the whole of a CSV file. Rows are as read; they may be ragged. */
data class Sheet(val name: String, val rows: List<List<Cell>>, val hidden: Boolean = false) {
    fun cell(row: Int, column: Int): Cell = rows.getOrNull(row)?.getOrNull(column) ?: Cell.Empty
}

/** A header row and the data under it. [firstRowNumber] is the 1-based spreadsheet row of the first data row. */
data class RawTable(
    val sheetName: String?,
    val headers: List<String>,
    val rows: List<List<Cell>>,
    val firstRowNumber: Int,
    /** Spreadsheet row numbers when rows were filtered, so problems still point at the right row. */
    val rowNumbers: List<Int>? = null,
) {
    fun rowNumber(index: Int): Int = rowNumbers?.getOrNull(index) ?: (firstRowNumber + index)

    /** A few non-blank values from a column, for the column mapper. */
    fun samples(column: Int, count: Int = 2): List<String> =
        rows.asSequence().map { it.getOrNull(column)?.asText().orEmpty() }.filter { it.isNotEmpty() }.take(count)
            .toList()

    companion object {
        /** The first non-blank row is the header; everything after it is data. */
        fun of(sheet: Sheet): RawTable? {
            val headerAt = sheet.rows.indexOfFirst { row -> row.any { !it.isBlank } }
            if (headerAt < 0) return null
            val headers = sheet.rows[headerAt].map { it.asText() }
            return RawTable(sheet.name, headers, sheet.rows.drop(headerAt + 1), headerAt + 2)
        }
    }
}

package app.fuelwise.core.domain.importing.xlsx

import app.fuelwise.core.domain.importing.Cell
import app.fuelwise.core.domain.importing.Sheet
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Reads the cell values of an .xlsx workbook (Office Open XML spreadsheet): sheet names and order, shared and
 * inline strings, numbers and booleans. Formatting is ignored; dates arrive as Excel serial numbers and the column
 * they're in decides how they're read. Size limits stop a crafted file from exhausting memory.
 */
object XlsxReader {
    /** True when [bytes] start like a zip archive, which an .xlsx is. */
    fun looksLikeXlsx(bytes: ByteArray): Boolean =
        bytes.size >= ZIP_HEADER && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun read(bytes: ByteArray): List<Sheet> {
        val parts = unzip(bytes)
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(it.decodeToString()) }.orEmpty()
        val targets = parts["xl/_rels/workbook.xml.rels"]?.let { relationships(it.decodeToString()) }.orEmpty()
        val workbook = requireNotNull(parts["xl/workbook.xml"]) { "Not a spreadsheet: no workbook" }.decodeToString()
        return sheetEntries(workbook).mapNotNull { entry ->
            val target = targets[entry.relationId] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val xml = parts[path] ?: return@mapNotNull null
            Sheet(entry.name, rows(xml.decodeToString(), shared), hidden = entry.hidden)
        }
    }

    private class SheetEntry(val name: String, val relationId: String, val hidden: Boolean)

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        val total = LongArray(1)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            // Only the parts this reader uses are kept; images and the rest are skipped unread.
            val wanted = { e: ZipEntry -> !e.isDirectory && (e.name.endsWith(".xml") || e.name.endsWith(".rels")) }
            generateSequence { zip.nextEntry }.filter(wanted).forEach { entry ->
                parts[entry.name.removePrefix("/")] = readEntry(zip, total)
            }
        }
        return parts
    }

    /** Reads the current entry; [total] counts bytes across entries against the size limit. */
    private fun readEntry(zip: ZipInputStream, total: LongArray): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var n = zip.read(buffer)
        while (n >= 0) {
            total[0] += n
            require(total[0] <= MAX_UNZIPPED_BYTES) { "Spreadsheet is too large" }
            out.write(buffer, 0, n)
            n = zip.read(buffer)
        }
        return out.toByteArray()
    }

    private fun sharedStrings(xml: String): List<String> {
        val strings = mutableListOf<String>()
        var current: StringBuilder? = null
        var inText = false
        var skipDepth = 0
        for (e in XmlReader(xml).events()) {
            when (e) {
                is XmlReader.Event.Start -> when {
                    e.name == "si" -> current = StringBuilder()

                    // Phonetic guides (rPh) hold furigana-style hints, not the cell's text.
                    e.name == "rPh" && !e.selfClosing -> skipDepth++

                    e.name == "t" && !e.selfClosing -> inText = true
                }

                is XmlReader.Event.End -> when (e.name) {
                    "si" -> {
                        strings += current?.toString().orEmpty()
                        current = null
                    }

                    "rPh" -> skipDepth--

                    "t" -> inText = false
                }

                is XmlReader.Event.Text -> if (inText && skipDepth == 0) current?.append(e.text)
            }
        }
        return strings
    }

    private fun starts(xml: String, name: String) =
        XmlReader(xml).events().filterIsInstance<XmlReader.Event.Start>().filter { it.name == name }

    private fun relationships(xml: String): Map<String, String> = starts(xml, "Relationship").mapNotNull { e ->
        val id = e.attributes["Id"]
        val target = e.attributes["Target"]
        if (id != null && target != null) id to target else null
    }.toMap()

    private fun sheetEntries(xml: String): List<SheetEntry> = starts(xml, "sheet").mapNotNull { e ->
        val id = e.attributes["id"] ?: return@mapNotNull null
        SheetEntry(e.attributes["name"].orEmpty(), id, hidden = e.attributes["state"] in setOf("hidden", "veryHidden"))
    }.toList()

    /** Reads `<row>`/`<c>` elements into dense rows. Missing cells and rows become [Cell.Empty] / empty rows. */
    private fun rows(xml: String, shared: List<String>): List<List<Cell>> {
        val collector = RowCollector(shared)
        for (e in XmlReader(xml).events()) {
            when (e) {
                is XmlReader.Event.Start -> collector.start(e)
                is XmlReader.Event.End -> collector.end(e.name)
                is XmlReader.Event.Text -> collector.text(e.text)
            }
        }
        return collector.rows()
    }

    /** Builds sparse rows from worksheet events. */
    private class RowCollector(private val shared: List<String>) {
        private val cells = sortedMapOf<Int, MutableMap<Int, Cell>>()
        private val cell = CellBuilder()
        private var rowIndex = -1
        private var nextColumn = 0

        fun start(e: XmlReader.Event.Start) {
            when (e.name) {
                "row" -> {
                    rowIndex = e.attributes["r"]?.toIntOrNull()?.minus(1) ?: (rowIndex + 1)
                    require(rowIndex < MAX_ROWS) { "Spreadsheet has too many rows" }
                    nextColumn = 0
                }

                "c" -> {
                    val column = e.attributes["r"]?.let(::columnIndex) ?: nextColumn
                    nextColumn = column + 1
                    cell.begin(column, e.attributes["t"])
                    if (e.selfClosing) finishCell()
                }

                "v", "t" -> cell.capturing = !e.selfClosing
            }
        }

        fun end(name: String) {
            when (name) {
                "c" -> finishCell()
                "v", "t" -> cell.capturing = false
            }
        }

        fun text(text: String) {
            if (cell.capturing) cell.text.append(text)
        }

        private fun finishCell() {
            cell.end(shared)?.let { cells.getOrPut(rowIndex) { mutableMapOf() }[cell.column] = it }
        }

        fun rows(): List<List<Cell>> {
            val last = cells.keys.lastOrNull() ?: return emptyList()
            return (0..last).map { r ->
                val row = cells[r] ?: return@map emptyList()
                val width = (row.keys.maxOrNull() ?: -1) + 1
                List(width) { c -> row[c] ?: Cell.Empty }
            }
        }
    }

    private class CellBuilder {
        var column = 0
        var type: String? = null
        var capturing = false
        val text = StringBuilder()

        fun begin(column: Int, type: String?) {
            this.column = column
            this.type = type
            text.setLength(0)
            capturing = false
        }

        fun end(shared: List<String>): Cell? {
            val raw = text.toString()
            capturing = false
            if (raw.isEmpty()) return null
            return when (type) {
                "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }?.let(Cell::Text)
                "str", "inlineStr" -> Cell.Text(raw)
                "b" -> Cell.Text(if (raw.trim() == "1") "TRUE" else "FALSE")
                "e" -> null
                else -> raw.trim().toDoubleOrNull()?.let(Cell::Number) ?: Cell.Text(raw)
            }
        }
    }

    /** "B12" → 1. */
    internal fun columnIndex(reference: String): Int? {
        val letters = reference.takeWhile { it.isLetter() }
        if (letters.isEmpty()) return null
        return letters.fold(0) { index, c -> index * ALPHABET + (c.uppercaseChar() - 'A' + 1) } - 1
    }

    private const val ZIP_HEADER = 4
    private const val BUFFER = 8 * 1024
    private const val MAX_UNZIPPED_BYTES = 64L * 1024 * 1024
    private const val MAX_ROWS = 50_000
    private const val ALPHABET = 26
}

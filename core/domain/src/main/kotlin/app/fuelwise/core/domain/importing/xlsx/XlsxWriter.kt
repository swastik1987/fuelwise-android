package app.fuelwise.core.domain.importing.xlsx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What a cell holds in a workbook this app writes. */
sealed interface XlsxValue {
    data class Text(val value: String) : XlsxValue

    data class Number(val value: Double) : XlsxValue

    data object Empty : XlsxValue
}

/** Cell formats the template needs. Index order matches the cellXfs list written to styles.xml. */
enum class XlsxStyle { Default, Header, Date, Time, Decimal, Money, Wrap, Note }

data class XlsxColumn(val header: String, val widthChars: Double, val style: XlsxStyle = XlsxStyle.Default)

/** A dropdown on one column, drawn from a range such as `Lists!$A$1:$A$18`. */
data class XlsxDropdown(val column: Int, val sourceRange: String)

data class XlsxSheet(
    val name: String,
    val columns: List<XlsxColumn>,
    val rows: List<List<XlsxValue>> = emptyList(),
    val dropdowns: List<XlsxDropdown> = emptyList(),
    val hidden: Boolean = false,
    /** Rows 2..[editableRows] get the column styles and dropdowns, so typed data is formatted. */
    val editableRows: Int = DEFAULT_EDITABLE_ROWS,
    val hasHeader: Boolean = true,
) {
    companion object {
        const val DEFAULT_EDITABLE_ROWS = 2000
    }
}

/**
 * Writes a minimal, valid .xlsx: one worksheet part per sheet with inline strings, column widths, a frozen header,
 * per-column formats and list dropdowns (data validation). Excel, Google Sheets and LibreOffice open it.
 */
object XlsxWriter {
    fun write(sheets: List<XlsxSheet>): ByteArray {
        require(sheets.any { !it.hidden }) { "A workbook needs a visible sheet" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.part("[Content_Types].xml", contentTypes(sheets.size))
            zip.part("_rels/.rels", ROOT_RELS)
            zip.part("xl/workbook.xml", workbook(sheets))
            zip.part("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.part("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sheet -> zip.part("xl/worksheets/sheet${i + 1}.xml", worksheet(sheet)) }
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.part(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(count: Int) = buildString {
        append(XML_HEADER)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"$SPREADSHEET_ML.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"$SPREADSHEET_ML.styles+xml\"/>")
        for (i in 1..count) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"$SPREADSHEET_ML.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun workbook(sheets: List<XlsxSheet>) = buildString {
        append(XML_HEADER)
        append(
            "<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\"><bookViews><workbookView activeTab=\"0\"/></bookViews>",
        )
        append("<sheets>")
        sheets.forEachIndexed { i, sheet ->
            val state = if (sheet.hidden) " state=\"hidden\"" else ""
            append("<sheet name=\"${XmlReader.escape(sheet.name)}\" sheetId=\"${i + 1}\"$state r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append(XML_HEADER)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..count) {
            append("<Relationship Id=\"rId$i\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${count + 1}\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun worksheet(sheet: XlsxSheet) = buildString {
        append(XML_HEADER)
        append("<worksheet xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\">")
        if (sheet.hasHeader) {
            append("<sheetViews><sheetView workbookViewId=\"0\">")
            append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            append("</sheetView></sheetViews>")
        }
        if (sheet.columns.isNotEmpty()) {
            append("<cols>")
            sheet.columns.forEachIndexed { i, column ->
                val style = column.style.ordinal
                append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"${column.widthChars}\" style=\"$style\" ")
                append("customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        val header = if (sheet.hasHeader) listOf(sheet.columns.map { XlsxValue.Text(it.header) }) else emptyList()
        (header + sheet.rows).forEachIndexed { r, row ->
            append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, value ->
                val style = when {
                    sheet.hasHeader && r == 0 -> XlsxStyle.Header
                    else -> sheet.columns.getOrNull(c)?.style ?: XlsxStyle.Default
                }
                appendCell(cellRef(c, r), value, style)
            }
            append("</row>")
        }
        append("</sheetData>")
        if (sheet.dropdowns.isNotEmpty()) {
            append("<dataValidations count=\"${sheet.dropdowns.size}\">")
            sheet.dropdowns.forEach { d ->
                val column = columnName(d.column)
                append("<dataValidation type=\"list\" allowBlank=\"1\" showErrorMessage=\"0\" ")
                append("sqref=\"${column}2:$column${sheet.editableRows}\">")
                append("<formula1>${XmlReader.escape(d.sourceRange)}</formula1></dataValidation>")
            }
            append("</dataValidations>")
        }
        append("</worksheet>")
    }

    private fun StringBuilder.appendCell(ref: String, value: XlsxValue, style: XlsxStyle) {
        val s = if (style == XlsxStyle.Default) "" else " s=\"${style.ordinal}\""
        when (value) {
            is XlsxValue.Text -> {
                append("<c r=\"$ref\" t=\"inlineStr\"$s><is><t xml:space=\"preserve\">")
                append(XmlReader.escape(value.value))
                append("</t></is></c>")
            }

            is XlsxValue.Number -> append("<c r=\"$ref\"$s><v>${value.value}</v></c>")

            XlsxValue.Empty -> if (s.isNotEmpty()) append("<c r=\"$ref\"$s/>")
        }
    }

    private fun cellRef(column: Int, row: Int) = columnName(column) + (row + 1)

    /** 0 → "A", 26 → "AA". */
    internal fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % ALPHABET
            name.insert(0, 'A' + rem)
            n = (n - 1) / ALPHABET
        }
        return name.toString()
    }

    private const val ALPHABET = 26
    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val SPREADSHEET_ML = "application/vnd.openxmlformats-officedocument.spreadsheetml"

    private const val ROOT_RELS = XML_HEADER +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    /**
     * Fonts: 0 normal, 1 bold, 2 grey italic. Fills: the two reserved ones plus a light header fill.
     * cellXfs follow [XlsxStyle]: Default, Header, Date (dd-mm-yyyy), Time (hh:mm), Decimal (0.00),
     * Money (#,##0.00), Wrap, Note.
     */
    private const val STYLES = XML_HEADER +
        "<styleSheet xmlns=\"$MAIN_NS\">" +
        "<numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"dd\\-mm\\-yyyy\"/>" +
        "<numFmt numFmtId=\"165\" formatCode=\"hh:mm\"/></numFmts>" +
        "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><i/><sz val=\"10\"/><color rgb=\"FF6B6B6B\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEDEDED\"/><bgColor indexed=\"64\"/></patternFill>" +
        "</fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"8\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
        "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\">" +
        "<alignment wrapText=\"1\" vertical=\"top\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\">" +
        "<alignment wrapText=\"1\" vertical=\"top\"/></xf>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}

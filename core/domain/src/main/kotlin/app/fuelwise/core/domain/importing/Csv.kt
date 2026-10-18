package app.fuelwise.core.domain.importing

/**
 * RFC 4180 CSV with the variations real exports have: a UTF-8 byte-order mark, `;` or tab delimiters (picked from
 * the first line), CRLF or LF line ends, and quoted fields containing delimiters, quotes ("") and line breaks.
 */
object Csv {
    fun parse(text: String, name: String = "CSV"): Sheet {
        val body = text.removePrefix(BOM)
        val delimiter = detectDelimiter(body)
        val rows = mutableListOf<List<Cell>>()
        var row = mutableListOf<Cell>()
        val field = StringBuilder()
        var quoted = false
        var i = 0

        fun endField() {
            row += if (field.isEmpty()) Cell.Empty else Cell.Text(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows += row
            row = mutableListOf()
        }

        while (i < body.length) {
            val c = body[i]
            when {
                quoted && c == '"' && body.getOrNull(i + 1) == '"' -> {
                    field.append('"')
                    i++
                }

                c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted

                quoted -> field.append(c)

                c == delimiter -> endField()

                c == '\r' && body.getOrNull(i + 1) == '\n' -> Unit

                c == '\n' || c == '\r' -> endRow()

                else -> field.append(c)
            }
            i++
            require(rows.size <= MAX_ROWS) { "File has too many rows" }
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        // Blank rows stay so row numbers match the file; trailing ones go.
        val last = rows.indexOfLast { r -> r.any { !it.isBlank } }
        return Sheet(name, rows.subList(0, last + 1))
    }

    /** The most common of `,` `;` and tab outside quotes on the first line; comma when none appear. */
    internal fun detectDelimiter(text: String): Char {
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        var quoted = false
        for (c in text) {
            if (c == '"') quoted = !quoted
            if (!quoted && (c == '\n' || c == '\r')) break
            if (!quoted && c in counts) counts[c] = counts.getValue(c) + 1
        }
        return counts.maxBy { it.value }.takeIf { it.value > 0 }?.key ?: ','
    }

    private val BOM = Char(BYTE_ORDER_MARK).toString()
    private const val BYTE_ORDER_MARK = 0xFEFF
    private const val MAX_ROWS = 50_000
}

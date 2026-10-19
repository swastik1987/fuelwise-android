package app.fuelwise.core.domain.scan

import java.math.BigDecimal

/**
 * A number found in a row after 7-segment confusions are undone ("2O0O.OO" → 2000.00). [decimals] is null when no
 * decimal point was read, which on LED pump displays often means the point segment was missed, not that there is none.
 */
internal data class NumberToken(
    val row: Int,
    val start: Int,
    val end: Int,
    val digits: String,
    val decimals: Int?,
    val box: OcrBox?,
    /** Letters read back as digits; each one makes the reading a little less certain. */
    val repairs: Int,
    /** Two runs joined across a gap where the decimal point should be ("2000 00"). */
    val joined: Boolean = false,
) {
    val integerDigits: String get() = digits.dropLast(decimals ?: 0)

    fun valueWith(decimalPlaces: Int): BigDecimal = BigDecimal(digits).movePointLeft(decimalPlaces)
}

/** Finds numbers in OCR text. Tuned for Indian pump displays and receipts; see the fixtures in core:domain tests. */
internal object OcrNumbers {
    /** Shapes a 7-segment or thermal-printed digit is commonly misread as. */
    private val CONFUSIONS = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', 'i' to '1', '|' to '1', '!' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5', '$' to '5',
        'G' to '6', 'b' to '6',
        'T' to '7',
        'B' to '8',
        'g' to '9', 'q' to '9',
    )
    private val RUN = Regex("[0-9.,OoDQIli|!ZzSs\$GbTBgq]+")

    /** Dates and clock times would otherwise read as amounts. Replaced by spaces so positions keep. */
    private val MASKS = listOf(
        Regex("""(?<!\d)\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}(?!\d)"""),
        Regex("""(?<!\d)\d{4}-\d{2}-\d{2}(?!\d)"""),
        Regex("""(?<!\d)\d{1,2}:\d{2}(:\d{2})?(?!\d)"""),
        Regex(
            """(?i)(?<!\d)\d{1,2}[\s-]*(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*[\s,-]*\d{2,4}(?!\d)""",
        ),
    )
    private const val MAX_DIGITS = 9
    private const val GROUP_DIGITS = 3
    private const val MAX_JOIN_HEAD = 5
    private val JOIN_TAIL = 2..3

    fun mask(text: String): String = MASKS.fold(text) { acc, regex ->
        regex.replace(acc) { " ".repeat(it.value.length) }
    }

    /** Every number in [row], plus joined readings for runs split where a decimal point was dropped. */
    fun scan(row: OcrRow): List<NumberToken> {
        val text = mask(row.text)
        val tokens = RUN.findAll(text).mapNotNull { token(row, text, it.range) }.toList()
        return tokens + joined(text, tokens)
    }

    private fun token(row: OcrRow, text: String, range: IntRange): NumberToken? {
        var start = range.first
        var end = range.last + 1
        // A confusable letter touching a word belongs to the word: "Rs.2000" is not "R5.2000".
        while (start < end && text[start] in CONFUSIONS && start > 0 && text[start - 1].isLetter()) start++
        while (end > start && text[end - 1] in CONFUSIONS && trailingLetter(text, start, end)) end--
        // Points and commas at the edges are punctuation ("Rs." or "2000.00,").
        while (start < end && text[start] in ".,") start++
        while (end > start && text[end - 1] in ".,") end--
        val raw = text.substring(start, end)
        val real = raw.count { it.isDigit() }
        val repairs = raw.count { it in CONFUSIONS }
        val embedded = start > 0 && text[start - 1].isLetter() && end < text.length && text[end].isLetter()
        if (real == 0 || repairs > real || embedded) return null
        val mapped = raw.map { CONFUSIONS[it] ?: it }.joinToString("")
        val (digits, decimals) = separators(mapped) ?: return null
        return NumberToken(row.index, start, end, digits, decimals, row.box, repairs)
    }

    /** The run's last char is a letter that belongs to the following word or unit ("19.43ltr", "19.43l"). */
    private fun trailingLetter(text: String, start: Int, end: Int): Boolean {
        if (end < text.length && text[end].isLetter()) return true
        val point = text.lastIndexOf('.', end - 1)
        return end == text.length && text[end - 1] == 'l' && point >= start && end - 1 - point > 2
    }

    /** Indian grouping ("1,23,456.00"), decimal commas ("19,43") and stray extra points. */
    private fun separators(text: String): Pair<String, Int?>? {
        var s = text
        if (',' in s) {
            val tail = s.substringAfterLast(',')
            s = if ('.' in s || (tail.length == GROUP_DIGITS && '.' !in tail)) {
                s.replace(",", "")
            } else {
                s.substringBeforeLast(',').replace(",", "") + "." + tail
            }
        }
        val point = s.lastIndexOf('.')
        val digits = s.replace(".", "")
        if (digits.isEmpty() || digits.length > MAX_DIGITS || !digits.all { it.isDigit() }) return null
        val decimals = if (point >= 0 && point < s.length - 1) s.length - 1 - point else null
        return digits to decimals
    }

    private fun joined(text: String, tokens: List<NumberToken>): List<NumberToken> =
        tokens.sortedBy { it.start }.zipWithNext().mapNotNull { (a, b) ->
            val gap = text.substring(a.end, b.start)
            val fits = a.decimals == null && b.decimals == null && a.digits.length <= MAX_JOIN_HEAD &&
                b.digits.length in JOIN_TAIL && gap.isNotEmpty() && gap.isBlank()
            if (!fits) return@mapNotNull null
            NumberToken(
                row = a.row,
                start = a.start,
                end = b.end,
                digits = a.digits + b.digits,
                decimals = b.digits.length,
                box = a.box,
                repairs = a.repairs + b.repairs,
                joined = true,
            )
        }
}

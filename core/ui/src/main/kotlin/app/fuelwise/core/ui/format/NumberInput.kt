package app.fuelwise.core.ui.format

/** Parsing for numeric text inputs. Accepts Indian grouping commas and spaces; one decimal point at most. */
object NumberInput {
    /** "42,318" → 42318.0, "12.5" → 12.5, "" → null, "1.2.3" → null. */
    fun parseDecimal(text: String): Double? {
        val cleaned = text.trim().filterNot { it == ',' || it == ' ' }
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        if (!cleaned.all { it.isDigit() || it == '.' }) return null
        return cleaned.toDoubleOrNull()
    }

    /** Keeps only what a decimal field may contain while typing, so stray characters never reach state. */
    fun sanitize(text: String, maxDecimals: Int = 2): String {
        val kept = StringBuilder()
        var seenDot = false
        var decimals = 0
        for (c in text) {
            when {
                c.isDigit() && (!seenDot || decimals < maxDecimals) -> {
                    kept.append(c)
                    if (seenDot) decimals++
                }

                c == '.' && !seenDot && maxDecimals > 0 -> {
                    seenDot = true
                    kept.append(c)
                }
            }
        }
        return kept.toString()
    }
}

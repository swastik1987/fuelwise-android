package app.fuelwise.core.domain.scan

/** What a printed label says a nearby number is. [Ignore] numbers are never used (density, nozzle, bill no.). */
internal enum class Role { Total, Quantity, Rate, Ignore }

internal data class Label(val role: Role, val start: Int, val end: Int)

/**
 * Labels on Indian pump displays and fuel receipts, English only: dispensers and OMC receipts print English. OCR
 * often reads O as 0 inside labels, so both are accepted.
 */
internal object ScanLabels {
    private const val O = "[O0]"

    /** Checked in this order; a later match that overlaps an earlier one is dropped ("RS/L" is a rate, not ₹). */
    private val VOCABULARY: List<Pair<Role, Regex>> = listOf(
        Role.Ignore to words(
            "DENSITY", "DENS", "KG\\s*/\\s*M3", "KG\\s*/\\s*M", "T${O}TALI[ZS]ER", "N${O}ZZLE", "N${O}Z", "PRESET",
            "TEMP", "INV${O}ICE\\s*N$O", "RECEIPT\\s*N$O", "BILL\\s*N$O", "TXN", "TRANS", "GSTIN", "GST",
            "TEL", "PH", "PHONE", "M${O}B", "M${O}BILE", "VEHICLE", "CARD", "FIP", "PUMP\\s*N$O", "ID",
        ),
        Role.Rate to words(
            "RATE",
            "PRICE",
            "UNIT\\s*PRICE",
            "RS\\.?\\s*/\\s*(L|LTR|LITRE|KG)",
            "₹\\s*/\\s*(L|LTR|KG)",
            "/\\s*(L|LTR|LITRE|KG)",
            "PER\\s*(L|LTR|LITRE|KG)",
        ),
        Role.Quantity to words(
            "V${O}LUME", "V${O}L", "LITRES?", "LITERS?", "LTRS?", "QTY", "QUANTITY", "KGS?", "L",
        ),
        Role.Total to words(
            "AM${O}UNT", "AMT", "SALE", "RUPEES", "RS", "INR", "₹", "T${O}TAL", "VALUE", "NET",
        ),
    )

    private fun words(vararg alternatives: String): Regex =
        Regex("(?<![A-Z])(${alternatives.joinToString("|")})(?![A-Z])")

    fun find(text: String): List<Label> {
        val upper = text.uppercase()
        val found = mutableListOf<Label>()
        VOCABULARY.forEach { (role, regex) ->
            regex.findAll(upper).forEach { match ->
                val label = Label(role, match.range.first, match.range.last + 1)
                if (found.none { it.start < label.end && label.start < it.end }) found += label
            }
        }
        return found.sortedBy { it.start }
    }

    /** The label a number at [start, end) belongs to: the nearest one before it, else the nearest after. */
    fun nearest(labels: List<Label>, start: Int, end: Int): Label? =
        labels.lastOrNull { it.end <= start } ?: labels.firstOrNull { it.start >= end }
}

package app.fuelwise.core.domain.importing

/** What a table holds: fill-ups and charges, or other expenses. */
enum class EntryKind { Fuel, Expense }

/** A value an imported row can carry. [kinds] says which tables use it. */
enum class ImportField(val kinds: Set<EntryKind>) {
    Vehicle(EntryKind.entries.toSet()),
    Date(EntryKind.entries.toSet()),
    Time(setOf(EntryKind.Fuel)),
    Odometer(EntryKind.entries.toSet()),
    EnergyType(setOf(EntryKind.Fuel)),
    TankNumber(setOf(EntryKind.Fuel)),
    Quantity(setOf(EntryKind.Fuel)),
    UnitPrice(setOf(EntryKind.Fuel)),
    Total(setOf(EntryKind.Fuel)),
    FullTank(setOf(EntryKind.Fuel)),
    MissedPrevious(setOf(EntryKind.Fuel)),
    Station(setOf(EntryKind.Fuel)),
    Category(setOf(EntryKind.Expense)),
    Amount(setOf(EntryKind.Expense)),
    TollPlaza(setOf(EntryKind.Expense)),
    Payment(EntryKind.entries.toSet()),
    Notes(EntryKind.entries.toSet()),
    ;

    companion object {
        /** The fields of [kind], in the order the template and the column mapper show them. */
        fun of(kind: EntryKind): List<ImportField> = entries.filter { kind in it.kinds }

        /** Fields a row can't do without. Fuel also needs two of quantity, price per unit and total. */
        fun required(kind: EntryKind): Set<ImportField> = when (kind) {
            EntryKind.Fuel -> setOf(Date, Odometer)
            EntryKind.Expense -> setOf(Date, Amount)
        }
    }
}

/** Which column (0-based) feeds each field. Unmapped fields are left out. */
data class ColumnMapping(val kind: EntryKind, val columns: Map<ImportField, Int>) {
    operator fun get(field: ImportField): Int? = columns[field]

    /** True when the required fields are mapped, and for fuel at least two of the price triangle. */
    val isComplete: Boolean
        get() {
            if (!columns.keys.containsAll(ImportField.required(kind))) return false
            if (kind == EntryKind.Expense) return true
            return listOf(ImportField.Quantity, ImportField.UnitPrice, ImportField.Total).count { it in columns } >= 2
        }

    /** A stable text form for remembering this mapping: "Fuel|Date=0,Odometer=1". */
    fun encode(): String = kind.name + "|" + columns.entries.joinToString(",") { "${it.key.name}=${it.value}" }

    companion object {
        fun decode(text: String): ColumnMapping? {
            val kind = EntryKind.entries.firstOrNull { it.name == text.substringBefore('|') } ?: return null
            val columns = text.substringAfter('|', "").split(',').filter { it.isNotBlank() }.mapNotNull { pair ->
                val field = ImportField.entries.firstOrNull { it.name == pair.substringBefore('=') }
                val column = pair.substringAfter('=').toIntOrNull()
                if (field == null || column == null) null else field to column
            }.toMap()
            return ColumnMapping(kind, columns)
        }

        /** Key for a remembered mapping: the normalized header row, so the same export layout maps the same way. */
        fun signature(headers: List<String>): String = headers.joinToString("|") { ImportVocabulary.normalize(it) }

        /**
         * Guesses a mapping from header names: exact matches first (the template's own labels among them), then
         * headers that start with a known name, such as Fuelio's "Odo (km)". Each column is used once.
         */
        fun suggest(headers: List<String>, kind: EntryKind, vocabulary: ImportVocabulary): ColumnMapping {
            val normalized = headers.map(ImportVocabulary::normalize)
            val used = mutableSetOf<Int>()
            val columns = mutableMapOf<ImportField, Int>()
            val fields = ImportField.of(kind)
            val passes: List<(String, String) -> Boolean> =
                listOf({ h, alias -> h == alias }, { h, alias -> alias.length >= MIN_PREFIX && h.startsWith(alias) })
            for (matches in passes) {
                fields.filter { it !in columns }.forEach { field ->
                    val aliases = vocabulary.headers[field].orEmpty().sortedByDescending { it.length }
                    normalized.indices.firstOrNull { i ->
                        i !in used && aliases.any { alias -> matches(normalized[i], alias) }
                    }?.let { at ->
                        columns[field] = at
                        used += at
                    }
                }
            }
            return ColumnMapping(kind, columns)
        }

        /** Fuel when the headers look like fill-ups (quantity or price per unit), expenses otherwise. */
        fun guessKind(headers: List<String>, vocabulary: ImportVocabulary): EntryKind {
            val fuel = suggest(headers, EntryKind.Fuel, vocabulary)
            return if (ImportField.Quantity in fuel.columns || ImportField.UnitPrice in fuel.columns) {
                EntryKind.Fuel
            } else {
                EntryKind.Expense
            }
        }

        private const val MIN_PREFIX = 3
    }
}

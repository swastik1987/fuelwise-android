package app.fuelwise.core.model

/** Where an import came from (PRD F-13.2). Stored by name on the batch. */
enum class ImportSource { Template, Fuelio, Spreadsheet }

/** One finished import, listed on the import screen with its undo. */
data class ImportBatch(
    val id: String,
    val source: ImportSource,
    val fileName: String?,
    val importedAtUtcMs: Long,
    val refuelCount: Int,
    val expenseCount: Int,
) {
    val entryCount: Int get() = refuelCount + expenseCount
}

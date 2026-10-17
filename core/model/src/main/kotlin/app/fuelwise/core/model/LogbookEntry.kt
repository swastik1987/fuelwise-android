package app.fuelwise.core.model

/** What kind of row a Logbook entry is; also the Logbook filter chips (design.md screen 12). */
enum class LogbookKind { Fuel, Charge, Expense, Toll }

/** Which table an entry lives in, so edit/delete reach the right record. */
enum class EntrySource { Refuel, Expense }

/**
 * One row of the unified timeline (PRD F-05): fill-ups, charges, expenses and tolls together.
 * [amount] is what was paid. [place] is the station for fuel and the plaza for tolls.
 */
data class LogbookEntry(
    val id: String,
    val source: EntrySource,
    val kind: LogbookKind,
    val vehicleId: String,
    val dateTimeUtcMs: Long,
    val amount: Money,
    val quantity: Double? = null,
    val unitPrice: Money? = null,
    val odometerKm: Double? = null,
    val tankId: String? = null,
    val categoryId: String? = null,
    val place: String? = null,
    val notes: String? = null,
    val isFull: Boolean = false,
) {
    companion object {
        val TOLL_CATEGORY_IDS = setOf(SystemCategory.TollFastag.id, SystemCategory.TollCash.id)

        fun of(refuel: Refuel) = LogbookEntry(
            id = refuel.id,
            source = EntrySource.Refuel,
            kind = if (refuel.socEndPct != null || refuel.chargerType != null) LogbookKind.Charge else LogbookKind.Fuel,
            vehicleId = refuel.vehicleId,
            dateTimeUtcMs = refuel.dateTimeUtcMs,
            amount = refuel.paid,
            quantity = refuel.quantity,
            unitPrice = refuel.unitPrice,
            odometerKm = refuel.odometerKm,
            tankId = refuel.tankId,
            place = refuel.stationName,
            notes = refuel.notes,
            isFull = refuel.isFull,
        )

        fun of(expense: Expense) = LogbookEntry(
            id = expense.id,
            source = EntrySource.Expense,
            kind = if (expense.categoryId in TOLL_CATEGORY_IDS) LogbookKind.Toll else LogbookKind.Expense,
            vehicleId = expense.vehicleId,
            dateTimeUtcMs = expense.dateTimeUtcMs,
            amount = expense.amount,
            odometerKm = expense.odometerKm,
            categoryId = expense.categoryId,
            place = expense.tollPlaza,
            notes = expense.notes,
        )
    }
}

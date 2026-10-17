package app.fuelwise.core.model

enum class RepeatUnit { Days, Weeks, Months, Years }

/**
 * A recurring expense such as an EMI or a parking pass (PRD F-04.3). Due entries are created as "auto" expenses
 * until confirmed; `recurrenceId + dueDate` makes creation idempotent across restores.
 */
data class Recurrence(
    val id: String,
    val vehicleId: String,
    val categoryId: String,
    val amount: Money,
    val every: Int,
    val unit: RepeatUnit,
    /** The first due date, local midnight in [zoneId], as UTC ms. */
    val startUtcMs: Long,
    val zoneId: String,
    val endUtcMs: Long? = null,
    val paymentMethod: PaymentMethod? = null,
    val notes: String? = null,
)

/** FASTag account per vehicle (PRD F-10.1). Only the last 4 digits of the tag are kept. */
data class FastagAccount(
    val id: String,
    val vehicleId: String,
    val issuer: String?,
    val tagLast4: String?,
    /** The balance the user typed, and when; the estimate runs from here (PRD F-10.3). */
    val anchorBalance: Money,
    val anchorAtUtcMs: Long,
    val lowBalanceThreshold: Money = Money(DEFAULT_LOW_BALANCE_PAISE),
) {
    companion object {
        const val DEFAULT_LOW_BALANCE_PAISE = 30_000L
    }
}

/** A FASTag top-up: a transfer, not a vehicle expense, so never in cost per km (PRD F-10.3). */
data class FastagRecharge(
    val id: String,
    val accountId: String,
    val vehicleId: String,
    val dateTimeUtcMs: Long,
    val zoneId: String,
    val amount: Money,
    val notes: String? = null,
)

package app.fuelwise.core.model

/**
 * A fill-up or an EV charge session (PRD F-02). Charges use the same record with the EV fields set.
 * [quantity] is in the tank's canonical unit (L, kg or kWh). [total] is the pump total; [paid] = total − discount
 * (a discount lowers what was paid, never the price per unit; PRD F-02.2).
 */
data class Refuel(
    val id: String,
    val vehicleId: String,
    val tankId: String,
    val dateTimeUtcMs: Long,
    val zoneId: String,
    val odometerKm: Double,
    val quantity: Double,
    val unitPrice: Money,
    val total: Money,
    val discount: Money = Money.ZERO,
    val isFull: Boolean,
    val missedPrevious: Boolean = false,
    val grade: FuelGrade? = null,
    val stationName: String? = null,
    val paymentMethod: PaymentMethod? = null,
    val notes: String? = null,
    val priceSource: PriceSource = PriceSource.Typed,
    val socStartPct: Double? = null,
    val socEndPct: Double? = null,
    val chargerType: ChargerType? = null,
    val chargeDurationMin: Int? = null,
    val operator: String? = null,
    /** Where it was logged, for the Logbook map (PRD F-05). Set by station auto-detect (F-21.1); null until then. */
    val lat: Double? = null,
    val lng: Double? = null,
) {
    val paid: Money get() = total - discount
}

/** A non-fuel cost (PRD F-04). Tolls are expenses in a toll category with [tollPlaza] set (PRD F-10.2). */
data class Expense(
    val id: String,
    val vehicleId: String,
    val categoryId: String,
    val dateTimeUtcMs: Long,
    val zoneId: String,
    val amount: Money,
    val odometerKm: Double? = null,
    val paymentMethod: PaymentMethod? = null,
    val notes: String? = null,
    val tollPlaza: String? = null,
    val isAutoCreated: Boolean = false,
    /** Set on entries a recurring expense created; with the date it keeps creation idempotent (PRD F-04.3). */
    val recurrenceId: String? = null,
    /** A toll or parking on a trip; its amount is added to the trip's cost (PRD F-07.1). */
    val tripId: String? = null,
)

enum class PaymentMethod { Upi, Card, Cash, FastagWallet, FleetCard }

/** Grade chips on the fill-up sheet. The premium label follows the station brand (XP95 / Speed / Power). */
enum class FuelGrade { Regular, E20, Premium }

enum class ChargerType { HomeAc, PublicAc, DcFast }

/** Where the price per unit came from, so the UI can label it (PRD F-02.3). */
enum class PriceSource { Typed, LastPaid, CityPrice, Scanned }

package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.Period
import app.fuelwise.core.domain.engine.divideBy
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.sum
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

/**
 * Total cost of ownership for a period (PRD F-03.5): everything spent running the vehicle, loan EMI and insurance
 * included, plus the period's share of the purchase price spread evenly over [years].
 */
data class OwnershipCost(
    /** Fuel and every expense in the period except purchase entries, which are the price itself. */
    val running: Money,
    val purchaseShare: Money,
    /** The purchase price being spread, summed across vehicles. */
    val purchasePrice: Money,
    val years: Int,
    val perKm: Money?,
    val perMonth: Money,
) {
    val total: Money get() = running + purchaseShare
}

/**
 * Null when no vehicle has a purchase price: the vehicle's price, or else its "Purchase" expenses. The spread starts
 * on the purchase date, else the first purchase expense, else when the vehicle was added; after [years] the share
 * is zero.
 */
fun ownershipCost(
    histories: List<VehicleHistory>,
    period: Period,
    km: Double?,
    years: Int,
    zone: ZoneId,
): OwnershipCost? {
    var price = Money.ZERO
    var share = Money.ZERO
    var running = Money.ZERO
    histories.forEach { (vehicle, refuels, expenses) ->
        val (purchases, others) = expenses.partition { it.categoryId == SystemCategory.Purchase.id }
        val vehiclePrice = vehicle.purchasePrice ?: purchases.map { it.amount }.sum().takeIf { it.paise > 0 }
        running += refuels.filter { it.dateTimeUtcMs in period }.map { it.paid }.sum() +
            others.filter { it.dateTimeUtcMs in period }.map { it.amount }.sum()
        if (vehiclePrice == null) return@forEach
        val start = vehicle.purchaseDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            ?: purchases.minOfOrNull { it.dateTimeUtcMs }
            ?: vehicle.startOdometerAtUtcMs
        val end = Instant.ofEpochMilli(start).atZone(zone).plusYears(years.toLong()).toInstant().toEpochMilli()
        val overlap = minOf(period.endUtcMs, end) - maxOf(period.startUtcMs, start)
        price += vehiclePrice
        if (overlap > 0) share += vehiclePrice.scaled(overlap, end - start)
    }
    if (price == Money.ZERO) return null
    val total = running + share
    return OwnershipCost(
        running = running,
        purchaseShare = share,
        purchasePrice = price,
        years = years,
        perKm = km?.takeIf { it > 0.0 }?.let { total.divideBy(it) },
        perMonth = total.divideBy(period.days / DAYS_PER_MONTH),
    )
}

/** [this] × part ÷ whole, rounded to the paisa. */
private fun Money.scaled(part: Long, whole: Long): Money = Money(
    BigDecimal.valueOf(paise).multiply(BigDecimal.valueOf(part))
        .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP)
        .longValueExact(),
)

private const val DAYS_PER_MONTH = 30.4375

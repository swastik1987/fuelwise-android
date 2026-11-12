package app.fuelwise.core.domain.engine

import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.sum
import java.math.BigDecimal
import java.math.RoundingMode

// "Fuel used" (owner decision 12 Nov 2026, user feedback): Home, Insights, the recap and the weekly digest cost a
// period by the fuel its km burnt, not by the fill-ups that happen to fall in it. A month with no fill-up still has a
// cost per km. PRD F-03.5. The money-paid basis stays in ComputeCostMetrics for reports, ownership and the goldens.

/** The vehicle's fuel cost per km from [fromUtcMs] on, in paise (not rounded: it's a rate, not an amount). */
data class FuelRate(val fromUtcMs: Long, val paisePerKm: BigDecimal)

/**
 * One vehicle's fuel use: odometer readings that include GPS and automatic trips since the last typed reading
 * ([readings]), and the fuel cost per km over time ([rates], oldest first; empty when there's no basis yet).
 */
data class FuelUse(val readings: List<OdometerReading>, val rates: List<FuelRate>) {
    val hasRate: Boolean get() = rates.isNotEmpty()
}

/**
 * Builds [FuelUse]. The rate, like a trip's cost estimate:
 * - one liquid or gas fuel: price of the latest fill before then ÷ km per unit (the user's own figure, F-01.4, else
 *   the measured full-tank average, else the claimed figure), stepping at each fill-up;
 * - bi-fuel and electric: everything paid across the odometer span ÷ that span, one rate for all time, so the
 *   mix of fuels actually bought is what counts.
 */
fun fuelUse(
    vehicle: Vehicle,
    refuels: List<Refuel>,
    readings: List<OdometerReading>,
    gps: List<GpsDistance>,
    consumption: ComputeConsumption = ComputeConsumption(),
): FuelUse = FuelUse(withGps(readings, gps), rates(vehicle, refuels.filter { it.vehicleId == vehicle.id }, consumption))

/**
 * [readings] plus two per GPS or automatic trip after the latest typed one, at its start and its end: the odometer the
 * estimate (PRD F-21.5) would show then. So a trip's km land when it was driven, not spread over the days before it.
 * Trips between typed readings add nothing: those readings already bound the distance.
 */
fun withGps(readings: List<OdometerReading>, gps: List<GpsDistance>): List<OdometerReading> {
    val latest = readings.maxWithOrNull(compareBy({ it.atUtcMs }, { it.odometerKm })) ?: return readings
    val after = gps.filter { it.atUtcMs > latest.atUtcMs }.sortedBy { it.atUtcMs }
    if (after.isEmpty()) return readings
    val factor = gpsOdometerFactor(readings, gps)
    var odometer = latest.odometerKm
    return readings + after.flatMap { trip ->
        val before = OdometerReading(odometer, trip.atUtcMs)
        odometer += trip.km * factor
        listOf(before, OdometerReading(odometer, maxOf(trip.endUtcMs, trip.atUtcMs)))
    }
}

private fun rates(vehicle: Vehicle, own: List<Refuel>, consumption: ComputeConsumption): List<FuelRate> {
    val tank = vehicle.primaryTank ?: return emptyList()
    if (!vehicle.isBiFuel && tank.energyType != EnergyType.ELECTRIC) {
        val fills = own.filter { it.tankId == tank.id }.sortedBy { it.dateTimeUtcMs }
        val engine = fills.map {
            FillUp(it.id, it.odometerKm, it.dateTimeUtcMs, it.quantity, it.isFull, it.missedPrevious)
        }
        val measured = consumption(engine).average?.kmPerUnit
        val kmPerUnit = (vehicle.custom.kmPerUnit ?: measured ?: vehicle.claimedKmPerUnit)?.takeIf { it > 0.0 }
        if (kmPerUnit != null) {
            vehicle.custom.pricePerUnit?.let { return listOf(FuelRate(Long.MIN_VALUE, perKm(it, kmPerUnit))) }
            if (fills.isNotEmpty()) {
                // Before the first fill, its price is the best guess.
                return fills.mapIndexed { i, f ->
                    FuelRate(if (i == 0) Long.MIN_VALUE else f.dateTimeUtcMs, perKm(f.unitPrice, kmPerUnit))
                }
            }
        }
    }
    return historyRate(own)?.let { listOf(FuelRate(Long.MIN_VALUE, it)) }.orEmpty()
}

/** Everything paid across the odometer span ÷ that span; the first fill is left out, its fuel was burnt before. */
private fun historyRate(own: List<Refuel>): BigDecimal? {
    val sorted = own.sortedWith(compareBy({ it.odometerKm }, { it.dateTimeUtcMs }))
    if (sorted.size < 2) return null
    val span = sorted.last().odometerKm - sorted.first().odometerKm
    if (span <= 0.0) return null
    val paid = sorted.drop(1).map { it.paid }.sum()
    return BigDecimal.valueOf(paid.paise).divide(BigDecimal.valueOf(span), RATE_SCALE, RoundingMode.HALF_UP)
}

private fun perKm(price: Money, kmPerUnit: Double): BigDecimal =
    BigDecimal.valueOf(price.paise).divide(BigDecimal.valueOf(kmPerUnit), RATE_SCALE, RoundingMode.HALF_UP)

/** Fuel used in [period]: km in each stretch between rate changes × that stretch's rate. Null without a rate. */
internal fun FuelUse.cost(period: Period, odometerAt: (Long) -> Double?): Money? {
    if (!hasRate) return null
    val cuts = rates.map { it.fromUtcMs }.filter { it > period.startUtcMs && it < period.endUtcMs }
    val bounds = listOf(period.startUtcMs) + cuts + period.endUtcMs
    var paise = BigDecimal.ZERO
    bounds.zipWithNext { from, to ->
        val end = odometerAt(to) ?: return@zipWithNext
        val start = odometerAt(from) ?: return@zipWithNext
        val km = (end - start).coerceAtLeast(0.0)
        val rate = rates.lastOrNull { it.fromUtcMs <= from } ?: rates.first()
        paise += rate.paisePerKm.multiply(BigDecimal.valueOf(km))
    }
    return Money(paise.setScale(0, RoundingMode.HALF_UP).longValueExact())
}

private const val RATE_SCALE = 6

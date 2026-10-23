package app.fuelwise.core.domain.engine

import app.fuelwise.core.model.Money
import app.fuelwise.core.model.sum
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** An odometer reading from any entry (fill-up, charge, expense with odometer, trip end). */
data class OdometerReading(val odometerKm: Double, val atUtcMs: Long)

/**
 * Distance a GPS or automatic trip recorded, at its start (PRD F-21.5). Not an odometer reading: GPS km aren't
 * odometer km.
 */
data class GpsDistance(val atUtcMs: Long, val km: Double)

/** Money spent on a vehicle. FASTag recharges are transfers, not spends, and never appear here (PRD F-10.3). */
data class Spend(
    val amount: Money,
    val atUtcMs: Long,
    val kind: Kind,
    /** From the expense category setting. Default excludes "Purchase" and "Loan EMI" (PRD F-03.5). */
    val includeInCostPerKm: Boolean = true,
) {
    enum class Kind { Energy, Expense }
}

/** A half-open time range [startUtcMs, endUtcMs). */
data class Period(val startUtcMs: Long, val endUtcMs: Long) {
    init {
        require(endUtcMs > startUtcMs) { "Period must end after it starts" }
    }

    operator fun contains(atUtcMs: Long) = atUtcMs in startUtcMs until endUtcMs

    val days: Double get() = (endUtcMs - startUtcMs).toDouble() / MS_PER_DAY
}

/** Distance driven in a period. [isEstimated] means it was interpolated from readings outside the period. */
data class PeriodDistance(val km: Double, val isEstimated: Boolean)

data class CostMetrics(
    /** Fuel or charging paid (amount after discount). */
    val energySpend: Money,
    /** Expenses that count toward cost per km. */
    val includedExpenses: Money,
    /** Expenses left out of cost per km (e.g. purchase, loan EMI). Still part of [totalSpend]. */
    val excludedExpenses: Money,
    val distance: PeriodDistance?,
    /** (energy + included expenses) ÷ km, rounded to the paisa. Null without distance. */
    val costPerKm: Money?,
    val energyCostPerKm: Money?,
    val costPerDay: Money,
    /** Cost per day scaled to an average month (30.4375 days). */
    val monthlyBurn: Money,
) {
    val totalSpend: Money get() = energySpend + includedExpenses + excludedExpenses
    val costBasis: Money get() = energySpend + includedExpenses
}

/** Period cost metrics (PRD F-03.5). Combined cost/km is exact for bi-fuel vehicles, so it's their hero metric. */
class ComputeCostMetrics @Inject constructor() {
    operator fun invoke(spends: List<Spend>, readings: List<OdometerReading>, period: Period): CostMetrics {
        val inPeriod = spends.filter { it.atUtcMs in period }
        val energy = inPeriod.filter { it.kind == Spend.Kind.Energy }.map { it.amount }.sum()
        val expenses = inPeriod.filter { it.kind == Spend.Kind.Expense }
        val included = expenses.filter { it.includeInCostPerKm }.map { it.amount }.sum()
        val excluded = expenses.filterNot { it.includeInCostPerKm }.map { it.amount }.sum()
        val distance = distanceInPeriod(readings, period)
        val basis = energy + included
        val perDay = basis.divideBy(period.days)
        return CostMetrics(
            energySpend = energy,
            includedExpenses = included,
            excludedExpenses = excluded,
            distance = distance,
            costPerKm = distance?.takeIf { it.km > 0.0 }?.let { basis.divideBy(it.km) },
            energyCostPerKm = distance?.takeIf { it.km > 0.0 }?.let { energy.divideBy(it.km) },
            costPerDay = perDay,
            monthlyBurn = basis.divideBy(period.days / DAYS_PER_MONTH),
        )
    }

    /**
     * Max − min odometer of readings inside the period. With fewer than two readings inside, the odometer at each
     * period boundary is interpolated from the closest readings on either side (clamped to known data, never
     * extrapolated past it), and the result is labelled estimated.
     */
    fun distanceInPeriod(readings: List<OdometerReading>, period: Period): PeriodDistance? {
        val inside = readings.filter { it.atUtcMs in period }
        if (inside.size >= 2) {
            return PeriodDistance(inside.maxOf { it.odometerKm } - inside.minOf { it.odometerKm }, isEstimated = false)
        }
        val sorted = readings.sortedWith(compareBy({ it.atUtcMs }, { it.odometerKm }))
        val start = odometerAt(sorted, period.startUtcMs)
        val end = odometerAt(sorted, period.endUtcMs)
        return if (start == null || end == null) {
            null
        } else {
            PeriodDistance((end - start).coerceAtLeast(0.0), isEstimated = true)
        }
    }

    private fun odometerAt(sorted: List<OdometerReading>, atUtcMs: Long): Double? {
        val before = sorted.lastOrNull { it.atUtcMs <= atUtcMs }
        val after = sorted.firstOrNull { it.atUtcMs >= atUtcMs }
        return when {
            before != null && after != null && after.atUtcMs > before.atUtcMs -> {
                val fraction = (atUtcMs - before.atUtcMs).toDouble() / (after.atUtcMs - before.atUtcMs)
                before.odometerKm + fraction * (after.odometerKm - before.odometerKm)
            }

            before != null -> before.odometerKm

            after != null -> after.odometerKm

            else -> null
        }
    }

    private companion object {
        const val DAYS_PER_MONTH = 30.4375
    }
}

/** Divides money by a positive quantity, rounding half-up to the paisa. */
internal fun Money.divideBy(divisor: Double): Money {
    require(divisor > 0.0) { "Divisor must be positive" }
    val paise = BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP)
    return Money(paise.longValueExact())
}

internal const val MS_PER_DAY = 86_400_000.0

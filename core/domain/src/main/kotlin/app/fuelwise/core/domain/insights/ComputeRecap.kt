package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.engine.Period
import app.fuelwise.core.domain.engine.Spend
import app.fuelwise.core.domain.engine.fuelUse
import app.fuelwise.core.domain.entry.gpsDistances
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Money
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** The shareable monthly recap card (PRD F-17, design.md screen 16). */
data class Recap(
    val month: YearMonth,
    val distanceKm: Double?,
    val spend: Money,
    val costPerKm: Money?,
    /** Best full-to-full tank closed this month. */
    val bestTank: Interval?,
    /** ₹/km vs the month before, as a ratio change. */
    val costPerKmChange: Double?,
    val energyType: EnergyType?,
)

class ComputeRecap @Inject constructor(
    private val consumption: ComputeConsumption,
    private val costMetrics: ComputeCostMetrics,
) {
    operator fun invoke(history: VehicleHistory, categories: List<Category>, month: YearMonth, zone: ZoneId): Recap {
        val included = categories.associate { it.id to it.includeInCostPerKm }
        val spends = history.refuels.map { Spend(it.paid, it.dateTimeUtcMs, Spend.Kind.Energy) } +
            history.expenses.map {
                Spend(
                    it.amount,
                    it.dateTimeUtcMs,
                    Spend.Kind.Expense,
                    included[it.categoryId] ?: true,
                )
            }
        val readings = odometerReadings(history.vehicle, history.refuels, history.expenses, history.trips)
        // Fuel used, as on Home (PRD F-03.5): a month without a fill-up still has a cost per km.
        val gps = gpsDistances(history.vehicle, history.trips)
        val use = fuelUse(history.vehicle, history.refuels, readings, gps, consumption)
        val current = costMetrics.fuelUsed(spends, use, month.period(zone))
        val previous = costMetrics.fuelUsed(spends, use, month.minusMonths(1).period(zone))

        val primary = history.vehicle.primaryTank
        val fills = history.refuels.filter { it.tankId == primary?.id }
        val byId = fills.associateBy { it.id }
        val monthPeriod = month.period(zone)
        val best = consumption(
            fills.map {
                FillUp(it.id, it.odometerKm, it.dateTimeUtcMs, it.quantity, it.isFull, it.missedPrevious)
            },
        )
            .entries
            .filter { e -> byId[e.id]?.dateTimeUtcMs?.let { it in monthPeriod } == true }
            .mapNotNull { (it.result as? IntervalResult.Measured)?.interval }
            .maxByOrNull { it.kmPerUnit }

        val cpk = current.costPerKm
        val prev = previous.costPerKm
        return Recap(
            month = month,
            distanceKm = current.distance?.km,
            spend = current.totalSpend,
            costPerKm = cpk,
            bestTank = best,
            costPerKmChange = if (cpk == null || prev == null ||
                prev.paise == 0L
            ) {
                null
            } else {
                cpk.paise.toDouble() / prev.paise - 1.0
            },
            energyType = primary?.energyType,
        )
    }
}

internal fun YearMonth.period(zone: ZoneId): Period = Period(
    atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.Period
import app.fuelwise.core.domain.engine.Spend
import app.fuelwise.core.domain.engine.fuelUse
import app.fuelwise.core.domain.entry.gpsDistances
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Last week (Monday to Sunday) for one vehicle: the Monday weekly digest (PRD §3 "Check", F-16 channels). */
data class WeeklyDigest(
    val weekStart: LocalDate,
    val costPerKm: Money,
    val distanceKm: Double,
    /** Everything spent that week, as on Home's "Spent" tile. */
    val spend: Money,
    /** Cost per km against the week before: −0.06 is 6% cheaper. Null when that week had no distance. */
    val changeVsPreviousWeek: Double?,
)

class ComputeWeeklyDigest @Inject constructor(private val costMetrics: ComputeCostMetrics) {
    /**
     * The digest for the week before the one containing [today]. [history] may hold every vehicle's entries; only
     * [VehicleHistory.vehicle]'s count. Null when that week has no cost per km: with no distance there's nothing
     * honest to say, so no notification goes out.
     */
    operator fun invoke(
        history: VehicleHistory,
        categories: List<Category>,
        trips: List<Trip>,
        today: LocalDate,
        zone: ZoneId,
    ): WeeklyDigest? {
        val vehicle = history.vehicle
        val refuels = history.refuels.filter { it.vehicleId == vehicle.id }
        val expenses = history.expenses.filter { it.vehicleId == vehicle.id }
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeek = thisWeek.minusWeeks(1)
        val included = categories.associate { it.id to it.includeInCostPerKm }
        val spends = refuels.map {
            Spend(it.paid, it.dateTimeUtcMs, Spend.Kind.Energy)
        } + expenses.map {
            Spend(it.amount, it.dateTimeUtcMs, Spend.Kind.Expense, included[it.categoryId] ?: true)
        }
        val readings = odometerReadings(vehicle, refuels, expenses, trips)
        // Fuel used, as on Home (PRD F-03.5).
        val use = fuelUse(vehicle, refuels, readings, gpsDistances(vehicle, trips))
        val week = costMetrics.fuelUsed(spends, use, period(lastWeek, thisWeek, zone))
        val before = costMetrics.fuelUsed(spends, use, period(lastWeek.minusWeeks(1), lastWeek, zone))
        val costPerKm = week.costPerKm ?: return null
        val distance = week.distance ?: return null
        return WeeklyDigest(
            weekStart = lastWeek,
            costPerKm = costPerKm,
            distanceKm = distance.km,
            spend = week.totalSpend,
            changeVsPreviousWeek = before.costPerKm?.paise?.takeIf { it > 0 }?.let {
                costPerKm.paise.toDouble() / it - 1.0
            },
        )
    }

    private fun period(from: LocalDate, to: LocalDate, zone: ZoneId) = Period(
        from.atStartOfDay(zone).toInstant().toEpochMilli(),
        to.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}

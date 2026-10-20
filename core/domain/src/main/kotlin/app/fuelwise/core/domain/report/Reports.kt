package app.fuelwise.core.domain.report

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.FillUp
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.engine.Period
import app.fuelwise.core.domain.engine.PeriodDistance
import app.fuelwise.core.domain.engine.Spend
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.sum
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** A report's dates: [start] to [endExclusive], local days. */
data class ReportPeriod(val start: LocalDate, val endExclusive: LocalDate) {
    init {
        require(endExclusive.isAfter(start)) { "A report period must end after it starts" }
    }

    fun toPeriod(zone: ZoneId): Period = Period(
        start.atStartOfDay(zone).toInstant().toEpochMilli(),
        endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    /** Inclusive last day, for labels. */
    val end: LocalDate get() = endExclusive.minusDays(1)

    companion object {
        fun month(month: YearMonth) = ReportPeriod(month.atDay(1), month.plusMonths(1).atDay(1))

        fun year(year: Year) = ReportPeriod(year.atDay(1), year.plusYears(1).atDay(1))
    }
}

/** Fill-ups or charges of one energy type in the period. [quantity] is canonical (L, kg, kWh). */
data class EnergyLine(val energyType: EnergyType, val fillUps: Int, val quantity: Double, val paid: Money)

data class CategoryLine(val categoryId: String, val entries: Int, val amount: Money)

/** Monthly or yearly vehicle report (PRD F-13.3). */
data class VehicleReport(
    val vehicle: Vehicle,
    val period: ReportPeriod,
    val distance: PeriodDistance?,
    val energy: List<EnergyLine>,
    /** Biggest first. */
    val categories: List<CategoryLine>,
    val energySpend: Money,
    val expenseSpend: Money,
    /** (fuel + expenses counted in cost per km) ÷ km, as on Insights. */
    val costPerKm: Money?,
    /** Full-to-full average of the primary tank, over tanks closed in the period. */
    val average: Interval?,
    val averageEnergy: EnergyType?,
) {
    val totalSpend: Money get() = energySpend + expenseSpend
    val isEmpty: Boolean get() = energy.isEmpty() && categories.isEmpty()
}

data class ServiceLine(val date: LocalDate, val odometerKm: Double?, val categoryId: String)

data class DocumentLine(val type: DocumentType, val expiry: LocalDate?)

/**
 * Seller's report (PRD F-13.3, Fuelio parity): what a buyer wants to see, with every price left out. It holds no
 * money at all, and no free-text notes, which could carry amounts.
 */
data class SellerReport(
    val vehicle: Vehicle,
    val odometerKm: Double,
    /** First day anything was logged for the vehicle. */
    val since: LocalDate?,
    val distanceLoggedKm: Double,
    val fillUps: Int,
    val average: Interval?,
    val averageEnergy: EnergyType?,
    /** Newest first. */
    val service: List<ServiceLine>,
    val documents: List<DocumentLine>,
)

data class BusinessTripLine(
    val tripId: String,
    val vehicleId: String,
    val date: LocalDate,
    val fromPlace: String?,
    val toPlace: String?,
    val notes: String?,
    val distanceKm: Double,
    val amount: Money,
)

/** Business mileage report (PRD F-07.9): business trips in a period × the user's ₹/km rate. */
data class BusinessMileageReport(
    val period: ReportPeriod,
    val ratePerKm: Money,
    /** Oldest first. */
    val trips: List<BusinessTripLine>,
) {
    val totalKm: Double get() = trips.sumOf { it.distanceKm }

    /** The sum of the rounded per-trip amounts, so the lines add up to the total. */
    val totalAmount: Money get() = trips.map { it.amount }.sum()
}

/** Builds the free reports from the user's data. Pure: rendering as text or CSV happens at the UI edge. */
class BuildReports @Inject constructor(
    private val costMetrics: ComputeCostMetrics,
    private val consumption: ComputeConsumption,
) {
    /** [history] may hold every vehicle's entries; only its own vehicle's count. */
    fun vehicle(
        history: VehicleHistory,
        categories: List<Category>,
        period: ReportPeriod,
        zone: ZoneId,
    ): VehicleReport {
        val vehicle = history.vehicle
        val mine = history.refuels.filter { it.vehicleId == vehicle.id }
        val myExpenses = history.expenses.filter { it.vehicleId == vehicle.id }
        val span = period.toPeriod(zone)
        val included = categories.associate { it.id to it.includeInCostPerKm }
        val spends = mine.map { Spend(it.paid, it.dateTimeUtcMs, Spend.Kind.Energy) } +
            myExpenses.map { Spend(it.amount, it.dateTimeUtcMs, Spend.Kind.Expense, included[it.categoryId] ?: true) }
        val metrics = costMetrics(spends, odometerReadings(vehicle, mine, myExpenses), span)
        val tanks = vehicle.tanks.associateBy { it.id }
        val fills = mine.filter { it.dateTimeUtcMs in span }
        val energy = fills.groupBy { tanks[it.tankId]?.energyType }
            .mapNotNull { (type, rows) ->
                type?.let { EnergyLine(it, rows.size, rows.sumOf { r -> r.quantity }, rows.map { r -> r.paid }.sum()) }
            }
            .sortedByDescending { it.paid }
        val categoryLines = myExpenses.filter { it.dateTimeUtcMs in span }.groupBy { it.categoryId }
            .map { (id, rows) -> CategoryLine(id, rows.size, rows.map { it.amount }.sum()) }
            .sortedByDescending { it.amount }
        return VehicleReport(
            vehicle = vehicle,
            period = period,
            distance = metrics.distance,
            energy = energy,
            categories = categoryLines,
            energySpend = metrics.energySpend,
            expenseSpend = metrics.includedExpenses + metrics.excludedExpenses,
            costPerKm = metrics.costPerKm,
            average = average(vehicle, mine, span),
            averageEnergy = vehicle.primaryTank?.energyType,
        )
    }

    fun seller(history: VehicleHistory, trips: List<Trip>, documents: List<Document>, zone: ZoneId): SellerReport {
        val vehicle = history.vehicle
        val mine = history.refuels.filter { it.vehicleId == vehicle.id }
        val myExpenses = history.expenses.filter { it.vehicleId == vehicle.id }
        val readings = odometerReadings(vehicle, mine, myExpenses, trips)
        val logged = (mine.map { it.dateTimeUtcMs } + myExpenses.map { it.dateTimeUtcMs }).minOrNull()
        return SellerReport(
            vehicle = vehicle,
            odometerKm = readings.maxOf { it.odometerKm },
            since = logged?.let { day(it, zone) },
            distanceLoggedKm = readings.maxOf { it.odometerKm } - readings.minOf { it.odometerKm },
            fillUps = mine.size,
            average = average(vehicle, mine, null),
            averageEnergy = vehicle.primaryTank?.energyType,
            service = myExpenses.filter { it.categoryId in SERVICE_CATEGORIES }
                .sortedByDescending { it.dateTimeUtcMs }
                .map { ServiceLine(day(it.dateTimeUtcMs, zone), it.odometerKm, it.categoryId) },
            documents = documents.filter { it.vehicleId == vehicle.id }
                .sortedWith(compareBy({ it.type.ordinal }, { it.expiryDateUtcMs }))
                .map { DocumentLine(it.type, it.expiryDateUtcMs?.let { ms -> day(ms, zone) }) },
        )
    }

    /** Finished business trips starting in [period], of the [vehicleIds] given (null: every vehicle). */
    fun businessMileage(
        trips: List<Trip>,
        vehicleIds: Set<String>?,
        period: ReportPeriod,
        ratePerKm: Money,
        zone: ZoneId,
    ): BusinessMileageReport {
        val span = period.toPeriod(zone)
        val lines = trips.filter {
            it.purpose == TripPurpose.Business && it.status == TripStatus.Done && it.startUtcMs in span &&
                (vehicleIds == null || it.vehicleId in vehicleIds)
        }.sortedBy { it.startUtcMs }.map { t ->
            BusinessTripLine(
                tripId = t.id,
                vehicleId = t.vehicleId,
                date = day(t.startUtcMs, zone),
                fromPlace = t.fromPlace,
                toPlace = t.toPlace,
                notes = t.notes,
                distanceKm = t.distanceKm,
                amount = reimbursement(ratePerKm, t.distanceKm),
            )
        }
        return BusinessMileageReport(period, ratePerKm, lines)
    }

    /** Sum of measured full-to-full intervals on the primary tank, closed in [span] (all time when null). */
    private fun average(vehicle: Vehicle, refuels: List<Refuel>, span: Period?): Interval? {
        val primary = vehicle.primaryTank ?: return null
        val fills = refuels.filter { it.tankId == primary.id }
        val at = fills.associate { it.id to it.dateTimeUtcMs }
        val intervals = consumption(
            fills.map { FillUp(it.id, it.odometerKm, it.dateTimeUtcMs, it.quantity, it.isFull, it.missedPrevious) },
        ).entries
            .filter { e -> span == null || at[e.id]?.let { it in span } == true }
            .mapNotNull { (it.result as? IntervalResult.Measured)?.interval }
        if (intervals.isEmpty()) return null
        return Interval(intervals.sumOf { it.distanceKm }, intervals.sumOf { it.quantity })
    }

    private fun day(utcMs: Long, zone: ZoneId) = Instant.ofEpochMilli(utcMs).atZone(zone).toLocalDate()

    companion object {
        /** Expense categories that make up the service history a buyer asks about. */
        val SERVICE_CATEGORIES: Set<String> = setOf(
            SystemCategory.Service.id,
            SystemCategory.Repair.id,
            SystemCategory.Tyres.id,
            SystemCategory.Battery.id,
        )

        /** Rate × km, rounded half-up to the paisa, without floating-point money (CLAUDE.md rule 1). */
        fun reimbursement(ratePerKm: Money, km: Double): Money = Money(
            BigDecimal.valueOf(ratePerKm.paise).multiply(BigDecimal.valueOf(km))
                .setScale(0, RoundingMode.HALF_UP).longValueExact(),
        )
    }
}

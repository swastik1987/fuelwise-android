package app.fuelwise.widget

import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.ObserveReminderStatuses
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.domain.summary.ComputeVehicleSummary
import app.fuelwise.core.domain.summary.VehicleSummary
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.Vehicle
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * What the Status widget shows for the selected vehicle (design.md screen 30: "₹4.82/km", "16.8 km/L",
 * "PUC · 12 days").
 */
data class StatusWidgetModel(
    val vehicleName: String,
    /** This month's cost per km; null until the month has a distance. */
    val costPerKm: Money?,
    /** Mask amounts, as Home does (PRD F-18 "Hide amounts"). */
    val hideAmounts: Boolean,
    /** Mileage of the last measured tank. */
    val lastKmPerUnit: Double?,
    val unit: QuantityUnit?,
    /** The vehicle's display units (PRD F-18) for the mileage figure. */
    val units: DisplayUnits = DisplayUnits.INDIA,
    val isEstimate: Boolean,
    val nextDue: ReminderStatus?,
)

/** The selected vehicle (or the first), and which one "Charge" should open: the selected EV, else any EV. */
data class WidgetVehicles(val selected: Vehicle?, val chargeVehicleId: String?)

fun widgetVehicles(vehicles: List<Vehicle>, selectedId: String?): WidgetVehicles {
    val selected = vehicles.firstOrNull { it.id == selectedId } ?: vehicles.firstOrNull()
    val charges: (Vehicle) -> Boolean = { v -> v.tanks.any { it.energyType == EnergyType.ELECTRIC } }
    val charge = selected?.takeIf(charges) ?: vehicles.firstOrNull(charges)
    return WidgetVehicles(selected, charge?.id)
}

/** Builds the model from the summary Home also shows; the widget computes nothing of its own. */
fun statusWidgetModel(
    vehicle: Vehicle,
    summary: VehicleSummary,
    statuses: List<ReminderStatus>,
    hideAmounts: Boolean,
    units: DisplayUnits = DisplayUnits.INDIA,
): StatusWidgetModel = StatusWidgetModel(
    vehicleName = vehicle.name,
    costPerKm = summary.month.costPerKm,
    hideAmounts = hideAmounts,
    lastKmPerUnit = summary.latestInterval?.kmPerUnit,
    unit = vehicle.primaryTank?.energyType?.canonicalUnit,
    units = units,
    isEstimate = summary.isEstimate,
    // Statuses come sorted by urgency, then nearest first; "Later" items aren't due enough to show (as on Home).
    nextDue = statuses.firstOrNull { it.reminder.vehicleId == vehicle.id && it.urgency != Urgency.Later },
)

/** How a due item reads: by days unless the km mark is clearly nearer (as Home's "Upcoming" pills). */
sealed interface DueLabel {
    data object Overdue : DueLabel

    data object Today : DueLabel

    data class Days(val days: Int) : DueLabel

    data class Km(val km: Double) : DueLabel
}

fun dueLabel(status: ReminderStatus): DueLabel? {
    val days = status.daysLeft
    val km = status.kmLeft
    return when {
        days != null && (km == null || days <= KM_DAYS_TIE) -> when {
            days < 0 -> DueLabel.Overdue
            days == 0L -> DueLabel.Today
            else -> DueLabel.Days(days.toInt())
        }

        km != null -> if (km < 0) DueLabel.Overdue else DueLabel.Km(km)

        else -> null
    }
}

private const val KM_DAYS_TIE = 30L

/** Reads what the widgets show, once per update (Glance redraws on [WidgetUpdater]'s signal, not on a timer). */
class WidgetDataSource @Inject constructor(
    private val vehicles: VehicleRepository,
    private val userData: UserDataRepository,
    private val entries: EntryRepository,
    private val trips: TripRepository,
    private val categories: CategoryRepository,
    private val statuses: ObserveReminderStatuses,
    private val summarize: ComputeVehicleSummary,
    private val zoneId: ZoneId,
) {
    suspend fun vehicles(): WidgetVehicles =
        widgetVehicles(vehicles.observeVehicles().first(), userData.userData.first().selectedVehicleId)

    /** Null without a vehicle. */
    suspend fun status(): StatusWidgetModel? {
        val vehicle = vehicles().selected ?: return null
        val summary = summarize(
            vehicle,
            entries.observeRefuels(vehicle.id).first(),
            entries.observeExpenses(vehicle.id).first(),
            categories.observeCategories().first(),
            zoneId,
            trips.observeTrips().first(),
        )
        val user = userData.userData.first()
        return statusWidgetModel(vehicle, summary, statuses.now(), user.hideAmounts, user.units.forVehicle(vehicle.id))
    }
}

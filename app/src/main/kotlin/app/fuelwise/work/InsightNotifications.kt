package app.fuelwise.work

import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.insights.ComputeWeeklyDigest
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.notifications.Notifier
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * The insight notifications the daily check sends (WorkManager, no exact alarms): the monthly recap on the 1st (PRD
 * F-17) and the Monday weekly digest (PRD §3 "Check"; onboarding: "A Monday summary of last week's ₹/km"). A rerun
 * the same day replaces the notification rather than adding another.
 */
class InsightNotifications @Inject constructor(
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val categories: CategoryRepository,
    private val trips: TripRepository,
    private val userData: UserDataRepository,
    private val compute: ComputeWeeklyDigest,
    private val notifier: Notifier,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    /** On the 1st, if last month has fill-ups: "Your October recap is ready". */
    suspend fun monthlyRecap() {
        val today = LocalDate.now(clock.withZone(zoneId))
        if (today.dayOfMonth != 1 || !userData.userData.first().monthlyRecap) return
        val last = YearMonth.from(today).minusMonths(1)
        val start = last.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val hadEntries = entries.observeAllRefuels().first().any { it.dateTimeUtcMs in start until end }
        if (hadEntries) notifier.recap(last.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
    }

    /** On Mondays, if turned on (off by default): last week's ₹/km for the selected vehicle. */
    suspend fun weeklyDigest() {
        val today = LocalDate.now(clock.withZone(zoneId))
        val user = userData.userData.first()
        if (today.dayOfWeek != DayOfWeek.MONDAY || !user.weeklyDigest) return
        val all = vehicles.observeVehicles().first()
        val vehicle = all.firstOrNull { it.id == user.selectedVehicleId } ?: all.firstOrNull() ?: return
        val digest = compute(
            VehicleHistory(vehicle, entries.observeAllRefuels().first(), entries.observeAllExpenses().first()),
            categories.observeCategories().first(),
            trips.observeTrips().first(),
            today,
            zoneId,
        ) ?: return
        notifier.weeklyDigest(
            vehicleName = vehicle.name,
            costPerKm = IndianNumberFormat.rupees(digest.costPerKm),
            km = IndianNumberFormat.decimal(digest.distanceKm, 0),
            spend = IndianNumberFormat.rupeesWhole(digest.spend),
            changePercent = digest.changeVsPreviousWeek?.let { (it * PERCENT).roundToInt() },
        )
    }

    private companion object {
        const val PERCENT = 100
    }
}

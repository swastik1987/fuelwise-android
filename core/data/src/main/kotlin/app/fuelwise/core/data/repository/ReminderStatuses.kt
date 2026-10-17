package app.fuelwise.core.data.repository

import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.domain.reminder.EvaluateReminders
import app.fuelwise.core.domain.reminder.OdometerOutlook
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.odometerWindowKm
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Reminder statuses for every active vehicle, with odometer reminders judged against the predicted odometer
 * (PRD F-11.3). Used by Home, the Reminders screen and the daily check.
 */
class ObserveReminderStatuses @Inject constructor(
    private val reminders: ReminderRepository,
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val rules: RulesRepository,
    private val predictions: ComputePredictions,
    private val evaluate: EvaluateReminders,
    private val zoneId: ZoneId,
) {
    operator fun invoke(): Flow<List<ReminderStatus>> = combine(
        reminders.observeReminders(),
        vehicles.observeVehicles(),
        entries.observeAllRefuels(),
        entries.observeAllExpenses(),
    ) { list, vehicleList, refuels, expenses ->
        val ruleSet = rules.reminderRules()
        val active = vehicleList.associateBy { it.id }
        val outlook = vehicleList.associate { v ->
            val readings = odometerReadings(v, refuels.filter { it.vehicleId == v.id }, expenses.filter { it.vehicleId == v.id })
            v.id to OdometerOutlook(predictions.predictedOdometer(readings), predictions.avgKmPerDay(readings), ruleSet.odometerWindowKm(v))
        }
        // Archived or deleted vehicles keep their rows but stop reminding.
        evaluate(list.filter { it.vehicleId in active }, outlook, zoneId)
    }

    suspend fun now(): List<ReminderStatus> = invoke().first()
}

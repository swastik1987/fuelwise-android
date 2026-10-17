package app.fuelwise.feature.home

import app.fuelwise.core.data.repository.ObserveReminderStatuses
import app.fuelwise.core.data.repository.RecurrenceRepository
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.model.Expense
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Home's reminders and recurring-entry cards for one vehicle (design.md screen 5, "Upcoming"). */
data class HomeCare(val upcoming: List<ReminderStatus> = emptyList(), val unconfirmed: List<Expense> = emptyList())

class HomeCareSource @Inject constructor(
    private val statuses: ObserveReminderStatuses,
    private val recurrences: RecurrenceRepository,
) {
    fun observe(vehicleId: String): Flow<HomeCare> =
        combine(statuses(), recurrences.observeUnconfirmed()) { all, auto ->
            HomeCare(
                upcoming = all.filter {
                    it.reminder.vehicleId == vehicleId && it.urgency != Urgency.Later
                }.take(MAX_PILLS),
                unconfirmed = auto.filter { it.vehicleId == vehicleId },
            )
        }

    suspend fun confirm(entries: List<Expense>) = entries.forEach { recurrences.confirm(it.id) }

    private companion object {
        const val MAX_PILLS = 3
    }
}

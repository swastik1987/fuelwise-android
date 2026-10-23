package app.fuelwise.core.domain.reminder

import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.SystemCategory

/**
 * The expense category a reminder's cost goes under (PRD F-11.4 "Done → optionally creates the expense").
 * Null when there is nothing to pay for: a FASTag top-up moves money into the wallet, the tolls are the cost.
 */
fun ReminderKind.costCategory(): SystemCategory? = when (this) {
    ReminderKind.Insurance -> SystemCategory.Insurance
    ReminderKind.Puc -> SystemCategory.Puc
    ReminderKind.Service, ReminderKind.EngineOil, ReminderKind.Coolant, ReminderKind.Wipers -> SystemCategory.Service
    ReminderKind.Tyres -> SystemCategory.Tyres
    ReminderKind.Battery -> SystemCategory.Battery
    ReminderKind.Rc, ReminderKind.Fitness -> SystemCategory.Registration
    ReminderKind.Warranty -> SystemCategory.ExtendedWarranty
    ReminderKind.Licence, ReminderKind.Custom -> SystemCategory.Other
    ReminderKind.FastagLowBalance -> null
}

/**
 * What the "Add cost" action after a "Done" carries to the expense sheet: the completion to link the expense to,
 * and the vehicle and category to start with. [encode] / [decode] round-trip it through a notification extra.
 */
data class ReminderCost(val completionId: String, val vehicleId: String, val categoryId: String) {
    fun encode(): String = listOf(VERSION, completionId, vehicleId, categoryId).joinToString(SEP)

    companion object {
        private const val VERSION = "1"
        private const val SEP = "\t"
        private const val FIELDS = 4

        fun decode(text: String?): ReminderCost? {
            val parts = text?.split(SEP)?.takeIf { it.size == FIELDS && it[0] == VERSION } ?: return null
            if (parts.drop(1).any(String::isBlank)) return null
            return ReminderCost(parts[1], parts[2], parts[3])
        }
    }
}

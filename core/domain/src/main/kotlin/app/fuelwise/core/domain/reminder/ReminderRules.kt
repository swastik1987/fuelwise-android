package app.fuelwise.core.domain.reminder

import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.Vehicle

/**
 * Default reminder rules, loaded from the versioned `assets/rules/india.json` (CLAUDE.md rule 8). Legal periods
 * such as PUC validity change, so they are data, never code, and every value stays editable on the reminder.
 */
data class ReminderRules(
    val version: Int,
    val presets: Map<ReminderKind, PresetRule>,
    /** PUC validity per emission norm; [pucDefault] when the norm is unknown or missing from the file. */
    val pucMonthsByNorm: Map<EmissionNorm, Int>,
    val pucDefault: PresetRule,
    /** "Due soon" distance for odometer reminders (PRD F-11.3). */
    val odometerWindowKmCar: Double,
    val odometerWindowKmTwoWheeler: Double,
)

/** A preset's defaults; [twoWheeler] overrides for scooters and motorcycles where the file has them. */
data class PresetRule(
    val months: Int? = null,
    val km: Double? = null,
    val notifyDaysBefore: List<Int> = emptyList(),
    val twoWheeler: PresetRule? = null,
)

/** What a new reminder of [kind] starts with for [vehicle]. */
fun ReminderRules.defaultsFor(kind: ReminderKind, vehicle: Vehicle): PresetRule {
    if (kind == ReminderKind.Puc) {
        val months = vehicle.emissionNorm?.let { pucMonthsByNorm[it] } ?: pucDefault.months
        return pucDefault.copy(months = months)
    }
    val rule = presets[kind] ?: return PresetRule(notifyDaysBefore = DEFAULT_NOTIFY)
    val chosen = if (vehicle.type.isTwoWheeler) rule.twoWheeler ?: rule else rule
    return chosen.copy(notifyDaysBefore = chosen.notifyDaysBefore.ifEmpty { rule.notifyDaysBefore.ifEmpty { DEFAULT_NOTIFY } })
}

fun ReminderRules.odometerWindowKm(vehicle: Vehicle): Double =
    if (vehicle.type.isTwoWheeler) odometerWindowKmTwoWheeler else odometerWindowKmCar

private val DEFAULT_NOTIFY = listOf(7, 1)

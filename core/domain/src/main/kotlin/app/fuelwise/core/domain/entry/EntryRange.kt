package app.fuelwise.core.domain.entry

import java.time.LocalDate
import java.time.ZoneId

/** Date ranges for the Logbook filter (PRD F-05). [Custom] uses dates the user picked. */
enum class EntryRangePreset { All, ThisMonth, LastThreeMonths, ThisYear, Custom }

/** A range in UTC ms: [fromUtcMs] inclusive, [toUtcMs] exclusive; null means open on that side. */
data class EntryRange(val fromUtcMs: Long?, val toUtcMs: Long?)

/**
 * The UTC bounds for a preset on [today], in the user's [zone] (local midnights). "Last 3 months" is this month and
 * the two before it, like Insights. [Custom] takes [customFrom]..[customTo], both days included.
 */
fun entryRange(
    preset: EntryRangePreset,
    today: LocalDate,
    zone: ZoneId,
    customFrom: LocalDate? = null,
    customTo: LocalDate? = null,
): EntryRange {
    fun start(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    val month = today.withDayOfMonth(1)
    return when (preset) {
        EntryRangePreset.All -> EntryRange(null, null)

        EntryRangePreset.ThisMonth -> EntryRange(start(month), start(month.plusMonths(1)))

        EntryRangePreset.LastThreeMonths -> EntryRange(start(month.minusMonths(2)), start(month.plusMonths(1)))

        EntryRangePreset.ThisYear -> EntryRange(
            start(today.withDayOfYear(1)),
            start(today.withDayOfYear(1).plusYears(1)),
        )

        EntryRangePreset.Custom -> EntryRange(customFrom?.let(::start), customTo?.let { start(it.plusDays(1)) })
    }
}

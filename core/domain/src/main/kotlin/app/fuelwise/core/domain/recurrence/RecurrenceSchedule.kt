package app.fuelwise.core.domain.recurrence

import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.FastagRecharge
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Recurrence
import app.fuelwise.core.model.RepeatUnit
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.sum
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Due dates of [recurrence] up to [untilUtcMs], oldest first, as local midnights in its zone (PRD F-04.3).
 * Month steps keep the day of month, clamped to short months (31 Jan → 28 Feb → 31 Mar).
 * At most [MAX_BACKFILL] dates, so a recurrence started long ago doesn't flood the logbook.
 */
fun Recurrence.dueDates(untilUtcMs: Long): List<Long> {
    val last = minOf(untilUtcMs, endUtcMs ?: Long.MAX_VALUE)
    return occurrences().takeWhile { it <= last }.toList().takeLast(MAX_BACKFILL)
}

/** The next due date after [afterUtcMs], or null past the end date. */
fun Recurrence.nextDue(afterUtcMs: Long): Long? =
    occurrences().takeWhile { it <= (endUtcMs ?: Long.MAX_VALUE) }.firstOrNull { it > afterUtcMs }

private fun Recurrence.occurrences(): Sequence<Long> {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val start = Instant.ofEpochMilli(startUtcMs).atZone(zone).toLocalDate()
    val step = every.coerceAtLeast(1).toLong()
    return generateSequence(0L) { it + 1 }.map { n -> start.advance(unit, n * step).atStartOfDay(zone).toInstant().toEpochMilli() }
}

private fun LocalDate.advance(unit: RepeatUnit, amount: Long): LocalDate = when (unit) {
    RepeatUnit.Days -> plusDays(amount)
    RepeatUnit.Weeks -> plusWeeks(amount)
    RepeatUnit.Months -> plusMonths(amount)
    RepeatUnit.Years -> plusYears(amount)
}

const val MAX_BACKFILL = 24

/** Estimated FASTag balance (PRD F-10.3): the anchor, plus recharges, minus FASTag tolls since the anchor. */
data class FastagBalance(val estimate: Money, val isLow: Boolean)

fun FastagAccount.balance(recharges: List<FastagRecharge>, expenses: List<Expense>): FastagBalance {
    val topUps = recharges.filter { it.accountId == id && it.dateTimeUtcMs >= anchorAtUtcMs }.map { it.amount }.sum()
    val tolls = expenses
        .filter { it.vehicleId == vehicleId && it.categoryId == SystemCategory.TollFastag.id && it.dateTimeUtcMs >= anchorAtUtcMs }
        .map { it.amount }
        .sum()
    val estimate = anchorBalance + topUps - tolls
    return FastagBalance(estimate, estimate.paise < lowBalanceThreshold.paise)
}

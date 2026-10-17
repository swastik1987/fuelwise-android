package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.Period
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The Insights period selector (PRD F-06.1). Calendar based: "3M" is this month and the two before it. */
enum class InsightPeriod {
    Month,
    ThreeMonths,
    SixMonths,
    YearToDate,
    TwelveMonths,
    All,
    ;

    /**
     * The range this period covers up to [nowUtcMs], and the equally long range just before it for "vs" deltas.
     * [All] starts at [earliestUtcMs] and has no previous range.
     */
    fun resolve(nowUtcMs: Long, zone: ZoneId, earliestUtcMs: Long?): Resolved {
        val today = Instant.ofEpochMilli(nowUtcMs).atZone(zone).toLocalDate()
        val thisMonth = YearMonth.from(today)
        val months = when (this) {
            Month -> 1L
            ThreeMonths -> QUARTER
            SixMonths -> HALF_YEAR
            TwelveMonths -> YEAR
            YearToDate -> today.monthValue.toLong()
            All -> null
        }
        val end = nowUtcMs
        if (months == null) {
            val start = minOf(earliestUtcMs ?: nowUtcMs, nowUtcMs - 1)
            return Resolved(Period(start, end + 1), previous = null)
        }
        val firstMonth = thisMonth.minusMonths(months - 1)
        val start = firstMonth.atDay(1).startOfDay(zone)
        val previousStart = firstMonth.minusMonths(months).atDay(1).startOfDay(zone)
        return Resolved(Period(start, maxOf(end, start + 1)), Period(previousStart, start))
    }

    data class Resolved(val current: Period, val previous: Period?)

    private companion object {
        const val QUARTER = 3L
        const val HALF_YEAR = 6L
        const val YEAR = 12L
    }
}

private fun LocalDate.startOfDay(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** Calendar months touched by [period], oldest first. */
fun Period.months(zone: ZoneId): List<YearMonth> {
    val first = YearMonth.from(Instant.ofEpochMilli(startUtcMs).atZone(zone))
    val last = YearMonth.from(Instant.ofEpochMilli(endUtcMs - 1).atZone(zone))
    return generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
}

/** The part of [month] inside [period]. */
fun Period.clip(month: YearMonth, zone: ZoneId): Period {
    val start = maxOf(startUtcMs, month.atDay(1).startOfDay(zone))
    val end = minOf(endUtcMs, month.plusMonths(1).atDay(1).startOfDay(zone))
    return Period(start, maxOf(end, start + 1))
}

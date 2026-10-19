package app.fuelwise.core.domain.prices

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the price feed may be fetched (PRD F-09.2): at most once a day, and only after 06:30 IST, half an hour after
 * the oil companies revise prices at 06:00. Always India time, whatever the phone's zone.
 */
object PriceFeedSchedule {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    val WINDOW_OPENS: LocalTime = LocalTime.of(6, 30)

    /** True when today's window (IST) is open and nothing was fetched since it opened. */
    fun isDue(now: Instant, lastFetchAt: Instant?): Boolean {
        val opens = todayOpens(now)
        if (now.isBefore(opens)) return false
        return lastFetchAt == null || lastFetchAt.isBefore(opens)
    }

    /**
     * Time from [now] to the next window opening plus [jitter], which spreads clients over the morning so they
     * don't all hit the static file at 06:30:00.
     */
    fun delayUntilNextWindow(now: Instant, jitter: Duration = Duration.ZERO): Duration {
        val opens = todayOpens(now)
        val next = if (now.isBefore(opens)) opens else opens.atZone(IST).plusDays(1).toInstant()
        return Duration.between(now, next).plus(jitter)
    }

    private fun todayOpens(now: Instant): Instant {
        val today = ZonedDateTime.ofInstant(now, IST).toLocalDate()
        return today.atTime(WINDOW_OPENS).atZone(IST).toInstant()
    }
}

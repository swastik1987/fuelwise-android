package app.fuelwise.core.domain.engine

import java.time.Clock
import javax.inject.Inject
import kotlin.math.ceil

/**
 * Predictions from the driving pattern (PRD F-03.6). Used for the odometer hint on the fill-up sheet, the date of
 * km-based reminders and range left. Predictions are hints only; a typed odometer always wins.
 */
class ComputePredictions @Inject constructor(private val clock: Clock) {
    /**
     * Average km per day over the last 90 days, falling back to all time. Needs at least two readings
     * spanning seven days or more; otherwise null.
     */
    fun avgKmPerDay(readings: List<OdometerReading>): Double? {
        val windowStart = clock.millis() - RECENT_WINDOW_DAYS * MS_PER_DAY.toLong()
        return rate(readings.filter { it.atUtcMs >= windowStart }) ?: rate(readings)
    }

    /** Today's odometer: the latest reading plus average km/day × days since. Null without readings. */
    fun predictedOdometer(readings: List<OdometerReading>): Double? {
        val latest = latest(readings) ?: return null
        val perDay = avgKmPerDay(readings) ?: return latest.odometerKm
        val daysSince = ((clock.millis() - latest.atUtcMs) / MS_PER_DAY).coerceAtLeast(0.0)
        return latest.odometerKm + perDay * daysSince
    }

    /**
     * When the odometer is expected to reach [targetKm] (UTC ms, rounded up to the whole day after the latest
     * reading). Returns the latest reading's time if already reached; null when there's no driving rate yet.
     */
    fun predictedTimeForOdometer(readings: List<OdometerReading>, targetKm: Double): Long? {
        val latest = latest(readings) ?: return null
        if (targetKm <= latest.odometerKm) return latest.atUtcMs
        val perDay = avgKmPerDay(readings)?.takeIf { it > 0.0 } ?: return null
        val days = ceil((targetKm - latest.odometerKm) / perDay)
        return latest.atUtcMs + (days * MS_PER_DAY).toLong()
    }

    /**
     * Estimated km left in the tank: capacity × km per unit − distance since the last fill, only when that last
     * fill was full. Null when any input is unknown. Never negative.
     */
    fun rangeLeftKm(
        tankCapacity: Double?,
        kmPerUnit: Double?,
        lastFill: FillUp?,
        currentOdometerKm: Double?,
    ): Double? {
        if (lastFill?.isFull != true) return null
        val capacity = tankCapacity ?: return null
        val efficiency = kmPerUnit ?: return null
        val odometer = currentOdometerKm ?: return null
        val driven = (odometer - lastFill.odometerKm).coerceAtLeast(0.0)
        return (capacity * efficiency - driven).coerceAtLeast(0.0)
    }

    private fun rate(readings: List<OdometerReading>): Double? {
        if (readings.size < 2) return null
        val first = readings.minWith(BY_TIME)
        val last = readings.maxWith(BY_TIME)
        val spanDays = (last.atUtcMs - first.atUtcMs) / MS_PER_DAY
        val km = last.odometerKm - first.odometerKm
        if (spanDays < MIN_SPAN_DAYS || km <= 0.0) return null
        return km / spanDays
    }

    private fun latest(readings: List<OdometerReading>) = readings.maxWithOrNull(BY_TIME)

    private companion object {
        const val RECENT_WINDOW_DAYS = 90L
        const val MIN_SPAN_DAYS = 7.0
        val BY_TIME = compareBy<OdometerReading>({ it.atUtcMs }, { it.odometerKm })
    }
}

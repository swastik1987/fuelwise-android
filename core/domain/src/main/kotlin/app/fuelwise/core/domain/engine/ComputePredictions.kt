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

    /**
     * Today's odometer. With GPS or automatic trips recorded since the latest reading (PRD F-21.5), the latest
     * reading plus their distance, scaled by this vehicle's own GPS-vs-odometer factor; otherwise the latest
     * reading plus average km/day × days since. Null without readings. A typed reading always wins: the estimate
     * only ever starts from one.
     */
    fun predictedOdometer(readings: List<OdometerReading>, gps: List<GpsDistance> = emptyList()): Double? {
        val latest = latest(readings) ?: return null
        val recorded = gps.filter { it.atUtcMs > latest.atUtcMs }.sumOf { it.km }
        if (recorded > 0.0) return latest.odometerKm + recorded * gpsFactor(readings, gps)
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

    /**
     * How far the odometer moves per GPS km, learned from this vehicle's own history (PRD F-21.5 calibration):
     * between consecutive readings, odometer km ÷ recorded km. Only intervals the recordings plausibly covered
     * (ratio [MIN_COVERED]–[MAX_COVERED]) count, so a week of unrecorded drives doesn't skew it. 1.0 until there are
     * some, and never outside [MIN_FACTOR]–[MAX_FACTOR].
     */
    fun gpsFactor(readings: List<OdometerReading>, gps: List<GpsDistance>): Double = gpsOdometerFactor(readings, gps)

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

/**
 * This vehicle's odometer km per GPS km (PRD F-21.5): from stretches between readings where GPS covered 90–130% of
 * the odometer distance, clamped to 0.9–1.2; 1 without such a stretch.
 */
fun gpsOdometerFactor(readings: List<OdometerReading>, gps: List<GpsDistance>): Double {
    var odometerKm = 0.0
    var gpsKm = 0.0
    readings.sortedWith(compareBy({ it.atUtcMs }, { it.odometerKm })).zipWithNext { a, b ->
        val driven = b.odometerKm - a.odometerKm
        val recorded = gps.filter { it.atUtcMs > a.atUtcMs && it.atUtcMs <= b.atUtcMs }.sumOf { it.km }
        if (driven > 0.0 && recorded > 0.0 && driven / recorded in MIN_COVERED..MAX_COVERED) {
            odometerKm += driven
            gpsKm += recorded
        }
    }
    return if (gpsKm > 0.0) (odometerKm / gpsKm).coerceIn(MIN_FACTOR, MAX_FACTOR) else 1.0
}

private const val MIN_COVERED = 0.9
private const val MAX_COVERED = 1.3
private const val MIN_FACTOR = 0.9
private const val MAX_FACTOR = 1.2

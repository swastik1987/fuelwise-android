package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.sum
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** What a manual trip's typed odometers and distance add up to (PRD F-07.1: start/end odometer, or distance). */
sealed interface TripDistance {
    data class Valid(val distanceKm: Double, val startOdometerKm: Double?, val endOdometerKm: Double?) : TripDistance

    /** Neither both odometer readings nor a distance. */
    data object Missing : TripDistance

    /** The end reading isn't after the start reading. */
    data object EndNotAfterStart : TripDistance
}

/**
 * Both readings win over a typed distance. A start reading plus a distance fills in the end reading, so the trip
 * still records where the odometer stood.
 */
fun resolveTripDistance(startOdometerKm: Double?, endOdometerKm: Double?, typedKm: Double?): TripDistance = when {
    startOdometerKm != null && endOdometerKm != null ->
        if (endOdometerKm > startOdometerKm) {
            TripDistance.Valid(endOdometerKm - startOdometerKm, startOdometerKm, endOdometerKm)
        } else {
            TripDistance.EndNotAfterStart
        }

    typedKm != null && typedKm > 0.0 ->
        TripDistance.Valid(typedKm, startOdometerKm, startOdometerKm?.let { it + typedKm } ?: endOdometerKm)

    else -> TripDistance.Missing
}

/** One person's share of a trip, rounded half-up to the whole rupee so friends pay a round amount (PRD F-07.4). */
data class TripSplit(val people: Int, val total: Money, val share: Money)

fun splitTripCost(total: Money, people: Int): TripSplit {
    require(people >= 1) { "At least one person" }
    val rupees = BigDecimal.valueOf(total.paise)
        .divide(BigDecimal.valueOf(people.toLong() * Money.PAISE_PER_RUPEE), 0, RoundingMode.HALF_UP)
    return TripSplit(people, total, Money.ofRupees(rupees.longValueExact()))
}

/**
 * A UPI "pay me" link (PRD F-07.4). The app only builds the link for the user to share; it never starts a payment.
 * Format from the NPCI UPI linking spec: `upi://pay?pa=&pn=&am=&cu=INR&tn=`.
 */
object UpiPayLink {
    private val VPA = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$")
    private const val MAX_NOTE = 50

    fun isValidVpa(vpa: String): Boolean = VPA.matches(vpa.trim())

    fun build(vpa: String, payeeName: String, amount: Money, note: String): String {
        require(isValidVpa(vpa)) { "Not a UPI ID" }
        val rupees = amount.paise / Money.PAISE_PER_RUPEE
        val am = String.format(Locale.ROOT, "%d.%02d", rupees, amount.paise % Money.PAISE_PER_RUPEE)
        return "upi://pay?pa=${encode(vpa.trim())}&pn=${encode(payeeName.trim())}&am=$am&cu=INR" +
            "&tn=${encode(note.trim().take(MAX_NOTE))}"
    }

    // UPI apps read %20 for spaces; URLEncoder writes "+". The String overload works on API 26 (minSdk).
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** Trips of one Monday-to-Sunday week (design.md screen 17; PRD F-07.7). */
data class TripWeek<T>(val start: LocalDate, val end: LocalDate, val trips: List<T>)

/** Groups trips by the local week they started in, newest week first, keeping the given order within a week. */
fun <T> groupByWeek(trips: List<T>, zone: ZoneId, startUtcMs: (T) -> Long): List<TripWeek<T>> =
    trips.groupBy { weekStart(Instant.ofEpochMilli(startUtcMs(it)).atZone(zone).toLocalDate()) }
        .toSortedMap(compareByDescending { it })
        .map { (start, list) -> TripWeek(start, start.plusDays(DAYS_IN_WEEK - 1), list) }

private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private const val DAYS_IN_WEEK = 7L

/** A trip's cost: its fuel estimate plus the tolls and parking linked to it (PRD F-07.1). Null when neither exists. */
fun tripCost(trip: Trip, expenses: List<Expense>): Money? {
    val linked = expenses.filter { it.tripId == trip.id }
    if (trip.estimatedFuelCost == null && linked.isEmpty()) return null
    return (trip.estimatedFuelCost ?: Money.ZERO) + linked.map { it.amount }.sum()
}

/** Average speed of a recorded GPS trip over its recorded time (pauses left out). Null for manual trips. */
fun Trip.averageKmh(): Double? {
    val seconds = recordedSeconds?.takeIf { it > 0 } ?: return null
    return distanceKm / (seconds / SECONDS_PER_HOUR)
}

private const val SECONDS_PER_HOUR = 3_600.0

package app.fuelwise.core.domain.trip

import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.sum
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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

/** Trips of one calendar month (design.md screen 17; PRD F-07.7; months since 12 Nov 2026, user feedback). */
data class TripMonth<T>(val month: YearMonth, val trips: List<T>)

/** Groups trips by the local month they started in, newest month first, keeping the given order within a month. */
fun <T> groupByMonth(trips: List<T>, zone: ZoneId, startUtcMs: (T) -> Long): List<TripMonth<T>> =
    trips.groupBy { YearMonth.from(Instant.ofEpochMilli(startUtcMs(it)).atZone(zone)) }
        .toSortedMap(compareByDescending { it })
        .map { (month, list) -> TripMonth(month, list) }

/** The Trips list date filter (user feedback, 12 Nov 2026). */
enum class TripRange { All, ThisMonth, LastMonth, ThreeMonths, ThisYear, Custom }

/**
 * The days [this] covers on [today], both ends included; null for [TripRange.All], and for [TripRange.Custom]
 * without a picked range. Three months is this month and the two before it.
 */
fun TripRange.days(today: LocalDate, from: LocalDate? = null, to: LocalDate? = null): ClosedRange<LocalDate>? {
    val month = YearMonth.from(today)
    return when (this) {
        TripRange.ThisMonth -> month.atDay(1)..today
        TripRange.LastMonth -> month.minusMonths(1).let { it.atDay(1)..it.atEndOfMonth() }
        TripRange.ThreeMonths -> month.minusMonths(2).atDay(1)..today
        TripRange.ThisYear -> today.withDayOfYear(1)..today
        TripRange.All -> null
        TripRange.Custom -> if (from == null || to == null) null else minOf(from, to)..maxOf(from, to)
    }
}

/** How many trips a vehicle finished in a month, and how far they went (Home trips tile). */
data class MonthTrips(val count: Int, val distanceKm: Double) {
    companion object {
        val NONE = MonthTrips(0, 0.0)
    }
}

/**
 * Finished trips of [vehicleId] that started in [month], local to [zone]. A trip still recording, paused or cut short
 * isn't counted until it's finished: its distance isn't final yet.
 */
fun monthTrips(trips: List<Trip>, vehicleId: String, month: YearMonth, zone: ZoneId): MonthTrips {
    val done = trips.filter {
        it.vehicleId == vehicleId &&
            it.status == TripStatus.Done &&
            YearMonth.from(Instant.ofEpochMilli(it.startUtcMs).atZone(zone)) == month
    }
    return MonthTrips(done.size, done.sumOf { it.distanceKm })
}

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

/**
 * Records the odometer the user typed after a GPS trip (PRD F-07.6). Without a start reading, the start is worked
 * back from the recorded distance. Null when the reading can't be right: not positive, below the start, or below
 * [floorKm], the last reading known before the trip.
 */
fun Trip.withEndOdometer(endKm: Double, floorKm: Double? = null): Trip? {
    if (endKm <= 0.0 || (floorKm != null && endKm < floorKm)) return null
    val start = startOdometerKm ?: (endKm - distanceKm).takeIf { it >= 0.0 }
    if (start != null && endKm < start) return null
    return copy(startOdometerKm = start, endOdometerKm = endKm)
}

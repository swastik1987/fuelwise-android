package app.fuelwise.core.domain.trip

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.model.CustomParameters
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Test

class TripTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val estimate = EstimateTripCost(ComputeConsumption())
    private val safari = vehicle("safari", Tank("diesel", EnergyType.DIESEL, 50.0, isPrimary = true))
    private val ertiga = vehicle(
        "ertiga",
        Tank("cng", EnergyType.CNG, 12.0, isPrimary = true),
        Tank("petrol", EnergyType.PETROL, 45.0, isPrimary = false),
    )

    @Test
    fun `single fuel - distance over average km per litre at the last price paid`() {
        // 600 km on 40 L between full fills = 15 km/L; last price ₹89.50/L.
        val fills = listOf(
            "safari".fill("a", "diesel", 40_000.0, 30.0, 8_800),
            "safari".fill("b", "diesel", 40_600.0, 40.0, 8_950),
        )
        val result = estimate(safari, fills, 150.0)!!
        val basis = result.basis as TripCostBasis.Efficiency
        assertThat(basis.kmPerUnit).isWithin(1e-9).of(15.0)
        assertThat(basis.quantity).isWithin(1e-9).of(10.0)
        // 10 L × ₹89.50
        assertThat(result.fuelCost).isEqualTo(Money(89_500))
        assertThat(basis.isManual).isFalse()
    }

    @Test
    fun `custom parameters override the measured figures`() {
        val custom = safari.copy(custom = CustomParameters(kmPerUnit = 12.5, pricePerUnit = Money(9_000)))
        val result = estimate(custom, emptyList(), 100.0)!!
        // 8 L × ₹90
        assertThat(result.fuelCost).isEqualTo(Money(72_000))
        assertThat((result.basis as TripCostBasis.Efficiency).isManual).isTrue()
    }

    @Test
    fun `bi-fuel uses the exact combined cost per km`() {
        val fills = listOf(
            "ertiga".fill("c1", "cng", 20_000.0, 8.0, 8_200),
            "ertiga".fill("p1", "petrol", 20_250.0, 10.0, 10_200),
            "ertiga".fill("c2", "cng", 20_500.0, 8.0, 8_200),
        )
        // Paid after the first entry: ₹1,020 + ₹656 over 500 km = ₹3.352 → ₹3.35/km.
        val result = estimate(ertiga, fills, 100.0)!!
        assertThat((result.basis as TripCostBasis.CostPerKm).costPerKm).isEqualTo(Money(335))
        assertThat(result.fuelCost).isEqualTo(Money(33_500))
    }

    @Test
    fun `no history and no custom figures - no estimate`() {
        assertThat(estimate(safari, emptyList(), 100.0)).isNull()
        assertThat(estimate(safari, listOf("safari".fill("a", "diesel", 1.0, 30.0, 8_800)), 50.0))
            .isNull()
        assertThat(estimate(safari, emptyList(), 0.0)).isNull()
    }

    @Test
    fun `other vehicles' fill-ups are ignored`() {
        val fills = listOf(
            "other".fill("a", "diesel", 40_000.0, 30.0, 8_800),
            "other".fill("b", "diesel", 40_600.0, 40.0, 8_950),
        )
        assertThat(estimate(safari, fills, 150.0)).isNull()
    }

    @Test
    fun `distance from both odometers, or typed`() {
        assertThat(resolveTripDistance(42_318.0, 42_464.0, 999.0))
            .isEqualTo(TripDistance.Valid(146.0, 42_318.0, 42_464.0))
        assertThat(resolveTripDistance(42_318.0, null, 146.0))
            .isEqualTo(TripDistance.Valid(146.0, 42_318.0, 42_464.0))
        assertThat(resolveTripDistance(null, null, 22.6)).isEqualTo(TripDistance.Valid(22.6, null, null))
        assertThat(resolveTripDistance(42_464.0, 42_318.0, null)).isEqualTo(TripDistance.EndNotAfterStart)
        assertThat(resolveTripDistance(42_318.0, null, null)).isEqualTo(TripDistance.Missing)
        assertThat(resolveTripDistance(null, null, 0.0)).isEqualTo(TripDistance.Missing)
    }

    @Test
    fun `split rounds each share to the rupee`() {
        assertThat(splitTripCost(Money(123_600), 3).share).isEqualTo(Money(41_200))
        // ₹1,000 / 3 = ₹333.33 → ₹333
        assertThat(splitTripCost(Money(100_000), 3).share).isEqualTo(Money(33_300))
        // ₹155 / 2 = ₹77.50 → ₹78
        assertThat(splitTripCost(Money(15_500), 2).share).isEqualTo(Money(7_800))
        assertThat(splitTripCost(Money(15_500), 1).share).isEqualTo(Money(15_500))
    }

    @Test
    fun `UPI link encodes the name and note`() {
        val link = UpiPayLink.build("asha.k@okaxis", "Asha K", Money(41_200), "Bengaluru → Mysuru")
        assertThat(link).isEqualTo(
            "upi://pay?pa=asha.k%40okaxis&pn=Asha%20K&am=412.00&cu=INR&tn=Bengaluru%20%E2%86%92%20Mysuru",
        )
    }

    @Test
    fun `UPI ID shape`() {
        assertThat(UpiPayLink.isValidVpa("asha.k@okaxis")).isTrue()
        assertThat(UpiPayLink.isValidVpa(" 98450-12345@ybl ")).isTrue()
        assertThat(UpiPayLink.isValidVpa("asha.k")).isFalse()
        assertThat(UpiPayLink.isValidVpa("asha k@okaxis")).isFalse()
        assertThat(UpiPayLink.isValidVpa("@okaxis")).isFalse()
    }

    @Test
    fun `trip cost adds linked tolls to the fuel estimate`() {
        val trip =
            Trip("t", "safari", day(10, 12), zone.id, 146.0, TripPurpose.Personal, estimatedFuelCost = Money(89_600))
        val toll = Expense("e1", "safari", "toll", day(10, 12), zone.id, Money(23_000), tripId = "t")
        val other = Expense("e2", "safari", "toll", day(10, 12), zone.id, Money(11_000), tripId = "x")
        assertThat(tripCost(trip, listOf(toll, other))).isEqualTo(Money(112_600))
        assertThat(tripCost(trip.copy(estimatedFuelCost = null), listOf(toll))).isEqualTo(Money(23_000))
        assertThat(tripCost(trip.copy(estimatedFuelCost = null), listOf(other))).isNull()
    }

    @Test
    fun `months, newest first, by the local day a trip started`() {
        // 31 Oct 23:30 IST is still October here, although it is 18:00 UTC.
        val late = LocalDate.of(2026, 10, 31).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val starts = listOf(day(11, 2), late, day(10, 12), day(9, 30))
        val months = groupByMonth(starts, zone) { it }
        assertThat(months.map { it.month }).containsExactly(
            YearMonth.of(2026, 11),
            YearMonth.of(2026, 10),
            YearMonth.of(2026, 9),
        ).inOrder()
        assertThat(months[1].trips).containsExactly(late, day(10, 12)).inOrder()
    }

    @Test
    fun `date filter ranges include both ends`() {
        val today = LocalDate.of(2026, 11, 12)
        assertThat(TripRange.ThisMonth.days(today)).isEqualTo(LocalDate.of(2026, 11, 1)..today)
        assertThat(TripRange.LastMonth.days(today))
            .isEqualTo(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 31))
        assertThat(TripRange.ThreeMonths.days(today)).isEqualTo(LocalDate.of(2026, 9, 1)..today)
        assertThat(TripRange.ThisYear.days(today)).isEqualTo(LocalDate.of(2026, 1, 1)..today)
        assertThat(TripRange.All.days(today)).isNull()
        // A range picked backwards still works; none picked yet means everything.
        val a = LocalDate.of(2026, 10, 20)
        val b = LocalDate.of(2026, 10, 5)
        assertThat(TripRange.Custom.days(today, a, b)).isEqualTo(b..a)
        assertThat(TripRange.Custom.days(today)).isNull()
    }

    private fun vehicle(id: String, vararg tanks: Tank) = Vehicle(
        id = id,
        name = id,
        type = VehicleType.Car,
        tanks = tanks.toList(),
        startOdometerKm = 0.0,
        startOdometerAtUtcMs = 0L,
    )

    // The receiver is the vehicle id.
    private fun String.fill(id: String, tankId: String, odo: Double, quantity: Double, pricePaise: Long) = Refuel(
        id = id,
        vehicleId = this,
        tankId = tankId,
        // Time follows the odometer; these tests don't depend on dates.
        dateTimeUtcMs = odo.toLong() * MS_PER_KM,
        zoneId = zone.id,
        odometerKm = odo,
        quantity = quantity,
        unitPrice = Money(pricePaise),
        total = Money((quantity * pricePaise).toLong()),
        isFull = true,
    )

    @Test
    fun `month trips - finished trips of the vehicle in the local month`() {
        fun trip(id: String, vehicle: String, startMs: Long, km: Double, status: TripStatus = TripStatus.Done) =
            Trip(id, vehicle, startMs, zone.id, km, TripPurpose.Personal, status = status)
        // 1 Oct 00:30 IST is still 30 Sep in UTC: it counts for October here.
        val earlyOct1 = LocalDate.of(2026, 10, 1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli()
        val trips = listOf(
            trip("a", "safari", day(10, 3), 23.4),
            trip("b", "safari", earlyOct1, 146.0),
            trip("c", "safari", day(9, 30), 40.0),
            trip("d", "nexon", day(10, 5), 12.0),
            trip("e", "safari", day(10, 18), 3.2, TripStatus.Recording),
            trip("f", "safari", day(10, 12), 8.0, TripStatus.Interrupted),
        )
        val result = monthTrips(trips, "safari", YearMonth.of(2026, 10), zone)
        assertThat(result.count).isEqualTo(2)
        assertThat(result.distanceKm).isWithin(1e-9).of(169.4)
        assertThat(monthTrips(emptyList(), "safari", YearMonth.of(2026, 10), zone)).isEqualTo(MonthTrips.NONE)
    }

    private fun day(month: Int, day: Int) =
        LocalDate.of(2026, month, day).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        const val MS_PER_KM = 60_000L
    }
}

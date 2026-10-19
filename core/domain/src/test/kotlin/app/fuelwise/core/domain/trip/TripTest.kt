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
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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
    fun `weeks run Monday to Sunday, newest first`() {
        // 19 Oct 2026 is a Monday.
        val starts = listOf(day(10, 19), day(10, 18), day(10, 13), day(10, 12))
        val weeks = groupByWeek(starts, zone) { it }
        assertThat(weeks.map { it.start }).containsExactly(
            LocalDate.of(2026, 10, 19),
            LocalDate.of(2026, 10, 12),
        ).inOrder()
        assertThat(weeks[1].end).isEqualTo(LocalDate.of(2026, 10, 18))
        assertThat(weeks[1].trips).hasSize(3)
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

    private fun day(month: Int, day: Int) =
        LocalDate.of(2026, month, day).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        const val MS_PER_KM = 60_000L
    }
}

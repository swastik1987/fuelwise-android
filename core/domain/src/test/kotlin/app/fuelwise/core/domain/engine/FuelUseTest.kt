package app.fuelwise.core.domain.engine

import app.fuelwise.core.model.CustomParameters
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

/** Fuel used (owner decision 12 Nov 2026; PRD F-03.5): km × the vehicle's own fuel cost per km. */
class FuelUseTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val metrics = ComputeCostMetrics()
    private val city = Vehicle(
        id = "city",
        name = "City",
        type = VehicleType.Car,
        tanks = listOf(Tank("petrol", EnergyType.PETROL, 40.0, isPrimary = true)),
        startOdometerKm = 10_000.0,
        startOdometerAtUtcMs = day(2026, 6, 1),
    )

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun midnight(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun fill(id: String, at: Long, odo: Double, litres: Double, pricePaise: Long, tank: String = "petrol") =
        Refuel(
            id = id,
            vehicleId = "city",
            tankId = tank,
            dateTimeUtcMs = at,
            zoneId = zone.id,
            odometerKm = odo,
            quantity = litres,
            unitPrice = Money(pricePaise),
            total = Money((litres * pricePaise).toLong()),
            isFull = true,
        )

    private fun readings(refuels: List<Refuel>) =
        listOf(OdometerReading(city.startOdometerKm, city.startOdometerAtUtcMs)) +
            refuels.map { OdometerReading(it.odometerKm, it.dateTimeUtcMs) }

    @Test
    fun `GPS trips after the last reading land when they were driven`() {
        val typed = listOf(OdometerReading(10_000.0, day(2026, 9, 1)))
        val trip = GpsDistance(day(2026, 9, 20), 50.0, endUtcMs = day(2026, 9, 20) + 3_600_000)
        val all = withGps(typed, listOf(trip))
        assertThat(all.map { it.odometerKm }).containsExactly(10_000.0, 10_000.0, 10_050.0).inOrder()
        // Before the trip started the car stood still: none of its km go to the days before.
        val september = metrics.distanceInPeriod(all, Period(day(2026, 9, 1), day(2026, 9, 19)))
        assertThat(september!!.km).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `a trip between typed readings adds nothing`() {
        val typed = listOf(OdometerReading(10_000.0, day(2026, 9, 1)), OdometerReading(10_400.0, day(2026, 9, 30)))
        assertThat(withGps(typed, listOf(GpsDistance(day(2026, 9, 10), 100.0)))).isEqualTo(typed)
    }

    @Test
    fun `the rate steps at each fill-up with its price`() {
        // 15 km/L throughout; ₹96.00 then ₹105.00 a litre: 640 then 700 paise per km.
        val refuels = listOf(
            fill("a", day(2026, 7, 1), 10_150.0, 40.0, 9_600),
            fill("b", day(2026, 8, 1), 10_750.0, 40.0, 9_600),
            fill("c", day(2026, 9, 1), 11_350.0, 40.0, 10_500),
        )
        val use = fuelUse(city, refuels, readings(refuels), emptyList())
        assertThat(use.rates.map { it.paisePerKm.toDouble() }).containsExactly(640.0, 640.0, 700.0).inOrder()

        // 1–31 Aug: 600 km between b and c, all at 640 (b's price) → ₹3,840; the August fill paid ₹4,200.
        val august = metrics.fuelUsed(emptyList(), use, Period(day(2026, 8, 1), day(2026, 9, 1)))
        assertThat(august.energySpend).isEqualTo(Money(384_000))
        assertThat(august.costPerKm).isEqualTo(Money(640))
    }

    @Test
    fun `km on both sides of a fill are costed at each side's price`() {
        val refuels = listOf(
            fill("a", day(2026, 7, 1), 10_150.0, 40.0, 9_600),
            fill("b", day(2026, 8, 1), 10_750.0, 40.0, 9_600),
            fill("c", day(2026, 9, 1), 11_350.0, 40.0, 10_500),
            fill("d", day(2026, 10, 1), 11_950.0, 40.0, 10_500),
        )
        val use = fuelUse(city, refuels, readings(refuels), emptyList())
        // 16 Aug – 16 Sep: about 300 km before c at 640, about 300 km after it at 700.
        val span = metrics.fuelUsed(emptyList(), use, Period(day(2026, 8, 16), day(2026, 9, 16)))
        val before = 600.0 * 16 / 31
        val after = 600.0 * 15 / 30
        assertThat(span.energySpend.paise.toDouble()).isWithin(1.0).of(before * 640 + after * 700)
    }

    @Test
    fun `the user's own mileage and price win`() {
        val manual = city.copy(custom = CustomParameters(kmPerUnit = 20.0, pricePerUnit = Money(10_000)))
        val refuels = listOf(fill("a", day(2026, 7, 1), 10_150.0, 40.0, 9_600))
        val use = fuelUse(manual, refuels, readings(refuels), emptyList())
        assertThat(use.rates.single().paisePerKm.toDouble()).isEqualTo(500.0)
    }

    @Test
    fun `bi-fuel uses everything paid over the distance, so the fuel mix counts`() {
        val bi = city.copy(
            tanks = listOf(
                Tank("petrol", EnergyType.PETROL, 37.0, isPrimary = true),
                Tank("cng", EnergyType.CNG, 12.0, isPrimary = false),
            ),
        )
        val refuels = listOf(
            fill("a", day(2026, 7, 1), 10_000.0, 10.0, 8_500, tank = "cng"),
            fill("b", day(2026, 7, 10), 10_250.0, 10.0, 8_500, tank = "cng"), // ₹850 for 250 km
            fill("c", day(2026, 7, 20), 10_500.0, 10.0, 10_300), // ₹1,030 for the next 250 km
        )
        val use = fuelUse(bi, refuels, readings(refuels), emptyList())
        // (₹850 + ₹1,030) / 500 km = 376 paise a km.
        assertThat(use.rates.single().paisePerKm.toDouble()).isWithin(1e-6).of(376.0)
    }

    @Test
    fun `no rate yet falls back to money paid`() {
        val refuels = listOf(fill("a", day(2026, 10, 2), 10_150.0, 40.0, 9_600))
        val use = fuelUse(city, refuels, readings(refuels), emptyList())
        assertThat(use.hasRate).isFalse()
        val paid = refuels.map { Spend(it.paid, it.dateTimeUtcMs, Spend.Kind.Energy) }
        val october = metrics.fuelUsed(paid, use, Period(midnight(2026, 10, 1), midnight(2026, 11, 1)))
        assertThat(october.energySpend).isEqualTo(Money(384_000))
        assertThat(october.energyEstimated).isFalse()
    }
}

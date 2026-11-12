package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.ComputeCostMetrics
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

class WeeklyDigestTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val digest = ComputeWeeklyDigest(ComputeCostMetrics())
    private val nexon = Vehicle(
        id = "nexon",
        name = "Nexon",
        type = VehicleType.Car,
        tanks = listOf(Tank("diesel", EnergyType.DIESEL, 44.0, isPrimary = true)),
        startOdometerKm = 40_000.0,
        startOdometerAtUtcMs = at(LocalDate.of(2026, 9, 28)),
    )

    // Monday 19 Oct 2026: the digest covers Monday 12 to Sunday 18 October.
    private val monday = LocalDate.of(2026, 10, 19)

    // ₹96.00/L at 12 km/L: fuel used costs 800 paise a km (PRD F-03.5, 12 Nov 2026).
    private val fills = listOf(
        fill("a", LocalDate.of(2026, 10, 5), 40_300.0, 30.0),
        fill("b", LocalDate.of(2026, 10, 12), 40_700.0, 400.0 / 12),
        fill("c", LocalDate.of(2026, 10, 18), 41_100.0, 400.0 / 12),
        // This week's fill isn't part of last week.
        fill("d", LocalDate.of(2026, 10, 19), 41_200.0, 100.0 / 12),
    )

    @Test
    fun `last week's cost per km, distance and spend`() {
        val result = digest(VehicleHistory(nexon, fills, emptyList()), emptyList(), emptyList(), monday, zone)!!
        assertThat(result.weekStart).isEqualTo(LocalDate.of(2026, 10, 12))
        // Monday 00:00 to Monday 00:00, interpolated between the fills either side: about 484 km.
        assertThat(result.distanceKm).isWithin(0.01).of(41_162.5 - 40_678.57)
        assertThat(result.spend.paise.toDouble()).isWithin(1.0).of(result.distanceKm * 800)
        assertThat(result.costPerKm).isEqualTo(Money(800))
        assertThat(result.changeVsPreviousWeek).isNotNull()
    }

    @Test
    fun `a week with no distance sends nothing`() {
        val one = listOf(fill("a", LocalDate.of(2026, 9, 30), 40_300.0, 30.0))
        assertThat(digest(VehicleHistory(nexon, one, emptyList()), emptyList(), emptyList(), monday, zone)).isNull()
    }

    @Test
    fun `other vehicles' fills don't count`() {
        val other = fill("x", LocalDate.of(2026, 10, 14), 90_000.0, 50.0).copy(vehicleId = "safari")
        val alone = digest(VehicleHistory(nexon, fills, emptyList()), emptyList(), emptyList(), monday, zone)!!
        val mixed = digest(VehicleHistory(nexon, fills + other, emptyList()), emptyList(), emptyList(), monday, zone)!!
        assertThat(mixed).isEqualTo(alone)
    }

    private fun fill(id: String, date: LocalDate, odo: Double, litres: Double) = Refuel(
        id = id,
        vehicleId = "nexon",
        tankId = "diesel",
        dateTimeUtcMs = at(date),
        zoneId = zone.id,
        odometerKm = odo,
        quantity = litres,
        unitPrice = Money(9_600),
        total = Money((litres * 9_600).toLong()),
        isFull = true,
    )

    private fun at(date: LocalDate) = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
}

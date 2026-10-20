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

    @Test
    fun `last week's cost per km, distance and spend`() {
        val fills = listOf(
            fill("a", LocalDate.of(2026, 10, 5), 40_300.0, 2_000_00),
            fill("b", LocalDate.of(2026, 10, 12), 40_700.0, 3_000_00),
            fill("c", LocalDate.of(2026, 10, 18), 41_100.0, 2_800_00),
            // This week's fill isn't part of last week.
            fill("d", LocalDate.of(2026, 10, 19), 41_200.0, 9_999_00),
        )
        val result = digest(VehicleHistory(nexon, fills, emptyList()), emptyList(), emptyList(), monday, zone)!!
        assertThat(result.weekStart).isEqualTo(LocalDate.of(2026, 10, 12))
        assertThat(result.spend).isEqualTo(Money(5_800_00))
        assertThat(result.distanceKm).isGreaterThan(0.0)
        assertThat(result.costPerKm.paise).isGreaterThan(0)
        assertThat(result.changeVsPreviousWeek).isNotNull()
    }

    @Test
    fun `a week with no distance sends nothing`() {
        val fills = listOf(fill("a", LocalDate.of(2026, 9, 30), 40_300.0, 2_000_00))
        assertThat(digest(VehicleHistory(nexon, fills, emptyList()), emptyList(), emptyList(), monday, zone)).isNull()
    }

    @Test
    fun `other vehicles' fills don't count`() {
        val other = fill("x", LocalDate.of(2026, 10, 14), 90_000.0, 5_000_00).copy(vehicleId = "safari")
        val fills = listOf(
            fill("b", LocalDate.of(2026, 10, 12), 40_700.0, 3_000_00),
            fill("c", LocalDate.of(2026, 10, 18), 41_100.0, 2_800_00),
            other,
        )
        val result = digest(VehicleHistory(nexon, fills, emptyList()), emptyList(), emptyList(), monday, zone)!!
        assertThat(result.spend).isEqualTo(Money(5_800_00))
    }

    private fun fill(id: String, date: LocalDate, odo: Double, totalPaise: Long) = Refuel(
        id = id,
        vehicleId = "nexon",
        tankId = "diesel",
        dateTimeUtcMs = at(date),
        zoneId = zone.id,
        odometerKm = odo,
        quantity = totalPaise / 8_895.0,
        unitPrice = Money(8_895),
        total = Money(totalPaise),
        isFull = true,
    )

    private fun at(date: LocalDate) = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
}

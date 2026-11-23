package app.fuelwise.core.domain.entry

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.LogbookKind
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Test

/** Logbook card figures (design "Logbook entry card", 23 Nov 2026) on a made-up Bengaluru diesel SUV. */
class LogbookFactsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val facts = LogbookFacts(ComputeConsumption())
    private val suv = Vehicle(
        id = "v",
        name = "Safari",
        type = VehicleType.Car,
        tanks = listOf(Tank("t", EnergyType.DIESEL, 50.0, isPrimary = true)),
        startOdometerKm = 35_000.0,
        startOdometerAtUtcMs = 0,
    )

    private fun at(date: String) = Instant.parse("${date}T10:00:00Z").toEpochMilli()

    private fun fill(id: String, date: String, odo: Double, litres: Double, full: Boolean = true) = Refuel(
        id = id,
        vehicleId = "v",
        tankId = "t",
        dateTimeUtcMs = at(date),
        zoneId = "Asia/Kolkata",
        odometerKm = odo,
        quantity = litres,
        unitPrice = Money(9_850),
        total = Money((litres * 9_850).toLong()),
        isFull = full,
    )

    private fun spend(id: String, date: String, odo: Double?, category: SystemCategory = SystemCategory.Service) =
        Expense(id, "v", category.id, at(date), "Asia/Kolkata", Money(457_300), odometerKm = odo)

    // Two 500 km tanks at 12.5 km/L set the average; then 358 km on 10 + 30 L, 9.0 km/L (well below).
    private val fills = listOf(
        fill("f0", "2026-10-01", 35_000.0, 40.0),
        fill("f1", "2026-10-15", 35_500.0, 40.0),
        fill("f2", "2026-11-01", 36_000.0, 40.0),
        fill("f3", "2026-11-10", 36_100.0, 10.0, full = false),
        fill("f4", "2026-11-23", 36_358.0, 30.0),
    )

    @Test
    fun `a fill shows its km per litre against the tank average, cost per km and km since the last fill`() {
        val result = facts(fills, emptyList(), listOf(suv), zone)

        val worse = result.getValue("f4") as EntryFacts.Fill
        // 358 km on 10 + 30 L since the full fill at 36,000.
        assertThat(worse.kmPerUnit).isWithin(0.01).of(358 / 40.0)
        assertThat(worse.band).isEqualTo(MileageBand.Worse)
        assertThat(worse.vsAverage!!).isLessThan(-0.10)
        assertThat(worse.kmSinceLast).isWithin(0.001).of(258.0)
        // ₹2,955 over 258 km.
        assertThat(worse.costPerKm).isEqualTo(Money(1_145))
        assertThat(worse.unit).isEqualTo(QuantityUnit.LITRE)

        // 12.5 km/L is 10.6% above the 11.3 km/L average.
        val better = result.getValue("f2") as EntryFacts.Fill
        assertThat(better.band).isEqualTo(MileageBand.Better)
    }

    @Test
    fun `fills without mileage say why`() {
        val result = facts(fills, emptyList(), listOf(suv), zone)
        assertThat((result.getValue("f0") as EntryFacts.Fill).noMileage).isEqualTo(NoMileage.Baseline)
        assertThat((result.getValue("f0") as EntryFacts.Fill).costPerKm).isNull()
        assertThat((result.getValue("f3") as EntryFacts.Fill).noMileage).isEqualTo(NoMileage.Partial)

        val missed = fills + fill("f5", "2026-11-30", 36_800.0, 35.0).copy(missedPrevious = true)
        assertThat((facts(missed, emptyList(), listOf(suv), zone).getValue("f5") as EntryFacts.Fill).noMileage)
            .isEqualTo(NoMileage.Restarted)
    }

    @Test
    fun `the band is plus or minus ten percent around the average`() {
        assertThat(MileageBand.of(0.10)).isEqualTo(MileageBand.Better)
        assertThat(MileageBand.of(0.099)).isEqualTo(MileageBand.Usual)
        assertThat(MileageBand.of(-0.10)).isEqualTo(MileageBand.Worse)
        assertThat(MileageBand.of(null)).isEqualTo(MileageBand.Usual)
    }

    @Test
    fun `an expense is measured from the last one in the same category`() {
        val expenses = listOf(
            spend("s1", "2026-03-04", 26_720.0),
            spend("b1", "2026-04-01", 27_000.0, SystemCategory.Battery),
            spend("s2", "2026-11-23", 30_662.0),
            spend("s3", "2026-11-24", null),
        )
        val result = facts(emptyList(), expenses, listOf(suv), zone)
        assertThat(
            result["s1"],
        ).isEqualTo(EntryFacts.Spend(hasEarlier = false, kmSinceSame = null, daysSinceSame = null))
        assertThat(
            result["b1"],
        ).isEqualTo(EntryFacts.Spend(hasEarlier = false, kmSinceSame = null, daysSinceSame = null))
        assertThat(
            result["s2"],
        ).isEqualTo(EntryFacts.Spend(hasEarlier = true, kmSinceSame = 3_942.0, daysSinceSame = 264))
        assertThat(result["s3"]).isEqualTo(EntryFacts.Spend(hasEarlier = true, kmSinceSame = null, daysSinceSame = 1))
    }

    @Test
    fun `month summaries follow the type filter`() {
        val result = facts(fills, listOf(spend("s", "2026-11-05", 36_050.0)), listOf(suv), zone)
        val entries = fills.map { LogbookEntry.of(it) } + LogbookEntry.of(spend("s", "2026-11-05", 36_050.0))
        val november = YearMonth.of(2026, 11)

        assertThat(MonthSummary.of(entries, emptySet(), result, zone)[november])
            .isEqualTo(MonthSummary.Counts(fills = 3, charges = 0, expenses = 1, tolls = 0))

        val fuel = MonthSummary.of(
            entries.filter { it.kind == LogbookKind.Fuel },
            setOf(LogbookKind.Fuel),
            result,
            zone,
        )[november] as MonthSummary.Energy
        assertThat(fuel.quantity).isWithin(0.001).of(80.0)
        assertThat(fuel.unit).isEqualTo(QuantityUnit.LITRE)
        assertThat(fuel.km).isWithin(0.001).of(858.0)
        // Measured intervals closed in November: 500 km on 40 L and 358 km on 40 L.
        assertThat(fuel.kmPerUnit).isWithin(0.001).of(858 / 80.0)

        val spends = MonthSummary.of(
            entries.filter { it.kind == LogbookKind.Expense },
            setOf(LogbookKind.Expense),
            result,
            zone,
        )
        assertThat(spends[november]).isEqualTo(MonthSummary.Categories(listOf(SystemCategory.Service.id)))
    }
}

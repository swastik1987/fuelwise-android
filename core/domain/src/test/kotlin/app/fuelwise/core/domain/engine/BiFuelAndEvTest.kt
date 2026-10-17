package app.fuelwise.core.domain.engine

import app.fuelwise.core.domain.engine.IntervalResult.Baseline
import app.fuelwise.core.domain.engine.IntervalResult.Invalid
import app.fuelwise.core.domain.engine.IntervalResult.Measured
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BiFuelAndEvTest {
    private val vehicle = ComputeVehicleConsumption(ComputeConsumption())
    private val ev = ComputeEvEfficiency()

    @Test
    fun `bi-fuel tanks are computed independently and marked as estimates`() {
        // Ertiga: CNG (kg) and petrol (L) share the same odometer.
        val fills = listOf(
            TankFill("cng", FillUp("c1", 31_000.0, 1, 10.0, isFull = true)),
            TankFill("petrol", FillUp("p1", 31_050.0, 2, 30.0, isFull = true)),
            TankFill("cng", FillUp("c2", 31_260.0, 3, 9.8, isFull = true)),
            TankFill("petrol", FillUp("p2", 31_900.0, 4, 6.0, isFull = true)),
        )
        val result = vehicle(fills, isBiFuel = true)

        assertThat(result.isEstimate).isTrue()
        assertThat((result.byTank.getValue("cng").resultFor("c2") as Measured).interval).isEqualTo(Interval(260.0, 9.8))
        assertThat((result.byTank.getValue("petrol").resultFor("p2") as Measured).interval)
            .isEqualTo(Interval(850.0, 6.0))
    }

    @Test
    fun `single-fuel vehicle is exact`() {
        val fills = listOf(TankFill("diesel", FillUp("d1", 42_000.0, 1, 40.0, isFull = true)))
        assertThat(vehicle(fills, isBiFuel = false).isEstimate).isFalse()
    }

    @Test
    fun `ev without SoC data falls back to the full-charge method on billed kWh`() {
        val sessions = listOf(
            ChargeSession("a", 18_000.0, 1, energyBilledKwh = 30.0, chargedToFull = true),
            ChargeSession("b", 18_150.0, 2, energyBilledKwh = 12.0),
            ChargeSession("c", 18_300.0, 3, energyBilledKwh = 10.0, socEndPct = 100.0),
        )
        val report = ev(sessions, usableCapacityKwh = 40.5)

        assertThat(report.batteryToWheel.resultFor("b")).isEqualTo(Invalid(Invalid.Reason.MissingSoc))
        assertThat(report.batteryToWheel.average).isNull()
        // a (full) → c (≥ 99% SoC counts as full): 300 km on 22 kWh billed.
        val wall = (report.wallToWheel.resultFor("c") as Measured).interval
        assertThat(wall).isEqualTo(Interval(300.0, 22.0))
        assertThat(report.chargingEfficiency).isEmpty()
    }

    @Test
    fun `soc that went up between sessions is flagged, not measured`() {
        val sessions = listOf(
            ChargeSession("a", 5_000.0, 1, 20.0, socStartPct = 20.0, socEndPct = 60.0),
            ChargeSession("b", 5_100.0, 2, 10.0, socStartPct = 70.0, socEndPct = 90.0),
        )
        val report = ev(sessions, usableCapacityKwh = 30.0)
        assertThat(report.batteryToWheel.resultFor("a")).isEqualTo(Baseline)
        assertThat(report.batteryToWheel.resultFor("b")).isEqualTo(Invalid(Invalid.Reason.InconsistentSoc))
    }

    @Test
    fun `battery-to-wheel average is weighted across sessions`() {
        val sessions = listOf(
            ChargeSession("a", 0.0, 1, 0.0, socStartPct = 10.0, socEndPct = 100.0),
            ChargeSession("b", 100.0, 2, 0.0, socStartPct = 50.0, socEndPct = 100.0), // 50% of 40 = 20 kWh / 100 km
            ChargeSession("c", 400.0, 3, 0.0, socStartPct = 25.0, socEndPct = 80.0), // 75% of 40 = 30 kWh / 300 km
        )
        val average = ev(sessions, usableCapacityKwh = 40.0).batteryToWheel.average!!
        assertThat(average.kmPerUnit).isWithin(0.001).of(8.0) // 400 km / 50 kWh
    }
}

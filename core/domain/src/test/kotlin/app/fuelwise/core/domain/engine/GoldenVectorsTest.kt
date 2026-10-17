package app.fuelwise.core.domain.engine

import app.fuelwise.core.domain.engine.IntervalResult.Baseline
import app.fuelwise.core.domain.engine.IntervalResult.Invalid
import app.fuelwise.core.domain.engine.IntervalResult.Measured
import app.fuelwise.core.domain.engine.IntervalResult.Partial
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * PRD §F-03.7 golden vectors. These must pass exactly (tolerance 0.001) after any engine change (CLAUDE.md rule 4).
 * Add a vector here for every engine bug fixed.
 */
class GoldenVectorsTest {
    private val consumption = ComputeConsumption()
    private val ev = ComputeEvEfficiency()

    private val liquidVector = listOf(
        fill("1", odo = 10_000.0, qty = 30.0, full = true),
        fill("2", odo = 10_300.0, qty = 10.0, full = false),
        fill("3", odo = 10_500.0, qty = 25.0, full = true),
        fill("4", odo = 10_900.0, qty = 28.0, full = true, missed = true),
        fill("5", odo = 11_350.0, qty = 30.0, full = true),
    )

    @Test
    fun `liquid vector - per-fill results`() {
        val report = consumption(liquidVector)

        assertThat(report.resultFor("1")).isEqualTo(Baseline)
        assertThat(report.resultFor("2")).isEqualTo(Partial)
        assertThat(report.resultFor("4")).isEqualTo(Invalid(Invalid.Reason.MissedPrevious))

        val third = (report.resultFor("3") as Measured).interval
        assertThat(third.distanceKm).isWithin(TOL).of(500.0)
        assertThat(third.quantity).isWithin(TOL).of(35.0)
        assertThat(third.kmPerUnit).isWithin(TOL).of(14.286)
        assertThat(third.per100Km).isWithin(TOL).of(7.000)

        val fifth = (report.resultFor("5") as Measured).interval
        assertThat(fifth.distanceKm).isWithin(TOL).of(450.0)
        assertThat(fifth.quantity).isWithin(TOL).of(30.0)
        assertThat(fifth.kmPerUnit).isWithin(TOL).of(15.000)
        assertThat(fifth.per100Km).isWithin(TOL).of(6.667)
    }

    @Test
    fun `liquid vector - weighted average`() {
        val average = consumption(liquidVector).average!!
        assertThat(average.distanceKm).isWithin(TOL).of(950.0)
        assertThat(average.quantity).isWithin(TOL).of(65.0)
        assertThat(average.kmPerUnit).isWithin(TOL).of(14.615)
        assertThat(average.per100Km).isWithin(TOL).of(6.842)
    }

    @Test
    fun `liquid vector - input order does not matter`() {
        assertThat(consumption(liquidVector.reversed())).isEqualTo(consumption(liquidVector))
    }

    @Test
    fun `ev vector - SoC method and charging efficiency`() {
        val sessions = listOf(
            ChargeSession(
                "A",
                odometerKm = 5_000.0,
                dateTimeUtcMs = 1,
                energyBilledKwh = 22.0,
                socStartPct = 20.0,
                socEndPct = 80.0,
            ),
            ChargeSession(
                "B",
                odometerKm = 5_150.0,
                dateTimeUtcMs = 2,
                energyBilledKwh = 20.0,
                socStartPct = 30.0,
                socEndPct = 90.0,
            ),
        )
        val report = ev(sessions, usableCapacityKwh = 30.0)

        val interval = (report.batteryToWheel.resultFor("B") as Measured).interval
        assertThat(interval.quantity).isWithin(TOL).of(15.0)
        assertThat(interval.distanceKm).isWithin(TOL).of(150.0)
        assertThat(interval.kmPerUnit).isWithin(TOL).of(10.0)
        assertThat(interval.per100Km).isWithin(TOL).of(10.0)
        assertThat(report.chargingEfficiency.getValue("B")).isWithin(TOL).of(0.90)
    }

    private fun fill(id: String, odo: Double, qty: Double, full: Boolean, missed: Boolean = false) =
        FillUp(id, odo, dateTimeUtcMs = odo.toLong(), quantity = qty, isFull = full, missedPrevious = missed)

    private companion object {
        const val TOL = 0.001
    }
}

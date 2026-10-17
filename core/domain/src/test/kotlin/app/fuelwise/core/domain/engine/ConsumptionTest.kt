package app.fuelwise.core.domain.engine

import app.fuelwise.core.domain.engine.IntervalResult.Baseline
import app.fuelwise.core.domain.engine.IntervalResult.Invalid
import app.fuelwise.core.domain.engine.IntervalResult.Measured
import app.fuelwise.core.domain.engine.IntervalResult.Partial
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlin.system.measureNanoTime
import org.junit.jupiter.api.Test

class ConsumptionTest {
    private val compute = ComputeConsumption()

    @Test
    fun `no fills gives an empty report`() {
        assertThat(compute(emptyList())).isEqualTo(ConsumptionReport.EMPTY)
    }

    @Test
    fun `one full fill is only a baseline`() {
        val report = compute(listOf(fill("a", 1_000.0, 40.0, full = true)))
        assertThat(report.resultFor("a")).isEqualTo(Baseline)
        assertThat(report.average).isNull()
    }

    @Test
    fun `partial fills before the first full fill are not counted`() {
        val report = compute(
            listOf(
                fill("p", 900.0, 15.0, full = false),
                fill("a", 1_000.0, 40.0, full = true),
                fill("b", 1_400.0, 30.0, full = true),
            ),
        )
        assertThat(report.resultFor("p")).isEqualTo(Partial)
        assertThat((report.resultFor("b") as Measured).interval).isEqualTo(Interval(400.0, 30.0))
    }

    @Test
    fun `missed-previous on a partial fill invalidates the interval it falls in`() {
        val report = compute(
            listOf(
                fill("a", 1_000.0, 40.0, full = true),
                fill("p", 1_200.0, 10.0, full = false, missed = true),
                fill("b", 1_500.0, 25.0, full = true),
                fill("c", 1_900.0, 30.0, full = true),
            ),
        )
        assertThat(report.resultFor("b")).isEqualTo(Invalid(Invalid.Reason.MissedPrevious))
        // The baseline reset at b, so c measures b→c only.
        assertThat((report.resultFor("c") as Measured).interval).isEqualTo(Interval(400.0, 30.0))
        assertThat(report.average).isEqualTo(Interval(400.0, 30.0))
    }

    @Test
    fun `same odometer twice is invalid, not a division by zero`() {
        val report = compute(
            listOf(
                fill("a", 1_000.0, 40.0, full = true, time = 1),
                fill("b", 1_000.0, 2.0, full = true, time = 2),
            ),
        )
        assertThat(report.resultFor("b")).isEqualTo(Invalid(Invalid.Reason.NoDistance))
        assertThat(report.average).isNull()
    }

    @Test
    fun `date-time breaks odometer ties`() {
        val early = fill("early", 1_000.0, 40.0, full = true, time = 1)
        val late = fill("late", 1_000.0, 5.0, full = false, time = 2)
        assertThat(compute(listOf(late, early)).entries.map { it.id }).containsExactly("early", "late").inOrder()
    }

    @Test
    fun `back-dated entry is re-sorted by odometer and recomputes later intervals`() {
        val before = listOf(
            fill("a", 1_000.0, 40.0, full = true),
            fill("c", 1_800.0, 50.0, full = true),
        )
        val withInserted = before + fill("b", 1_400.0, 28.0, full = true)
        val report = compute(withInserted)
        assertThat((report.resultFor("b") as Measured).interval).isEqualTo(Interval(400.0, 28.0))
        assertThat((report.resultFor("c") as Measured).interval).isEqualTo(Interval(400.0, 50.0))
    }

    @Test
    fun `average is weighted, not a mean of interval means`() {
        val report = compute(
            listOf(
                fill("a", 0.0, 10.0, full = true),
                fill("b", 100.0, 10.0, full = true), // 10 km/L
                fill("c", 1_100.0, 50.0, full = true), // 20 km/L
            ),
        )
        // Mean of means would be 15 km/L; the honest figure is 1,100 km / 60 L.
        assertThat(report.average!!.kmPerUnit).isWithin(0.001).of(18.333)
    }

    @Test
    fun `recomputes 2,000 entries within 200 ms (PRD F-02 AC)`() {
        val random = Random(42)
        var odo = 10_000.0
        val fills = List(2_000) { i ->
            odo += random.nextDouble(150.0, 450.0)
            fill("f$i", odo, random.nextDouble(5.0, 45.0), full = random.nextInt(5) != 0, time = i.toLong())
        }.shuffled(random)

        repeat(3) { compute(fills) } // warm up the JIT
        val elapsedMs = measureNanoTime { compute(fills) } / 1_000_000
        assertThat(elapsedMs).isLessThan(200L)
    }

    private fun fill(id: String, odo: Double, qty: Double, full: Boolean, missed: Boolean = false, time: Long = 0) =
        FillUp(id, odo, dateTimeUtcMs = time, quantity = qty, isFull = full, missedPrevious = missed)
}

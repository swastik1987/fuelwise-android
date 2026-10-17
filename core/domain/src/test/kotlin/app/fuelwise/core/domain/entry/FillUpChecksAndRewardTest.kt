package app.fuelwise.core.domain.entry

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FillUpChecksAndRewardTest {
    private val check = CheckFillUp()
    private val reward = ComputeFillReward(ComputeConsumption())

    @Test
    fun `odometer must exceed the previous entry by date`() {
        val previous = fill("a", time = 100, odo = 31_045.0)
        val result =
            check(
                dateTimeUtcMs = 200,
                odometerKm = 30_980.0,
                quantity = 30.0,
                tankCapacity = 45.0,
                existing = listOf(previous),
            )
        assertThat(result.problems).containsExactly(CheckFillUp.Problem.OdometerNotAbovePrevious(previous))
    }

    @Test
    fun `a back-dated entry must stay below the next one`() {
        val next = fill("b", time = 300, odo = 42_000.0)
        val result =
            check(
                dateTimeUtcMs = 200,
                odometerKm = 42_100.0,
                quantity = 30.0,
                tankCapacity = 50.0,
                existing = listOf(next),
            )
        assertThat(result.problems).containsExactly(CheckFillUp.Problem.OdometerNotBelowNext(next))
    }

    @Test
    fun `short-fill guard warns above tank capacity plus 3 percent`() {
        assertThat(
            check(0, null, 47.20, 45.0, emptyList()).warnings,
        ).containsExactly(CheckFillUp.Warning.MoreThanTank(45.0))
        assertThat(check(0, null, 46.30, 45.0, emptyList()).warnings).isEmpty()
    }

    @Test
    fun `first full fill sets the baseline and a partial shows nothing`() {
        val first = fill("a", 1, 42_000.0, qty = 40.0, full = true)
        assertThat(reward(listOf(first), "a")).isEqualTo(FillReward.Baseline)
        val partial = fill("b", 2, 42_200.0, qty = 10.0, full = false)
        assertThat(reward(listOf(first, partial), "b")).isEqualTo(FillReward.Partial)
    }

    @Test
    fun `closing fill reports km per unit, change vs average and cost per km`() {
        val fills = listOf(
            fill("a", 1, 41_000.0, qty = 30.0, full = true),
            fill("b", 2, 41_400.0, qty = 30.0, full = true, paid = 310_000), // 13.333 km/L
            fill("c", 3, 41_600.0, qty = 10.0, full = false, paid = 100_000),
            fill("d", 4, 42_000.0, qty = 30.0, full = true, paid = 300_000), // 600 km / 40 L = 15 km/L
        )
        val result = reward(fills, "d") as FillReward.Measured

        assertThat(result.interval.kmPerUnit).isWithin(1e-9).of(15.0)
        assertThat(result.changeVsAverage!!).isWithin(1e-6).of(15.0 / (400.0 / 30.0) - 1) // +12.5%
        assertThat(result.costPerKm).isEqualTo(Money(667)) // ₹4,000 / 600 km = ₹6.666… → ₹6.67
        assertThat(result.unusual).isFalse()
    }

    @Test
    fun `a big jump against recent tanks is flagged unusual`() {
        val fills = listOf(
            fill("a", 1, 10_000.0, qty = 30.0, full = true),
            fill("b", 2, 10_450.0, qty = 30.0, full = true), // 15 km/L
            fill("c", 3, 10_550.0, qty = 30.0, full = true), // 3.3 km/L — likely a typo
        )
        assertThat((reward(fills, "c") as FillReward.Measured).unusual).isTrue()
    }

    private fun fill(
        id: String,
        time: Long,
        odo: Double,
        qty: Double = 30.0,
        full: Boolean = true,
        paid: Long = 300_000,
    ) = Refuel(
        id = id,
        vehicleId = "v",
        tankId = "t",
        dateTimeUtcMs = time,
        zoneId = "Asia/Kolkata",
        odometerKm = odo,
        quantity = qty,
        unitPrice = Money(10_000),
        total = Money(paid),
        isFull = full,
    )
}

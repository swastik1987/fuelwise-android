package app.fuelwise.core.domain.prices

import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PriceAlertsTest {
    private val detect = DetectPriceAlerts()
    private val before = mapOf(
        PriceFuel.Petrol to Money(10_292),
        PriceFuel.Diesel to Money(8_895),
        PriceFuel.Cng to Money(8_900),
    )

    @Test
    fun `a move of one rupee or more alerts, either way`() {
        val after = mapOf(
            PriceFuel.Petrol to Money(10_412),
            PriceFuel.Diesel to Money(8_795),
            PriceFuel.Cng to Money(8_999),
            PriceFuel.PremiumPetrol to Money(11_000),
        )
        val changes = detect(enabled = true, fromFeed = true, before = before, after = after)
        assertThat(changes).containsExactly(
            PriceChange(PriceFuel.Petrol, Money(10_292), Money(10_412)),
            PriceChange(PriceFuel.Diesel, Money(8_895), Money(8_795)),
        ).inOrder()
        assertThat(changes.first().delta).isEqualTo(Money(120))
    }

    @Test
    fun `off, or not from the feed, never alerts`() {
        val after = mapOf(PriceFuel.Petrol to Money(10_592))
        assertThat(detect(enabled = false, fromFeed = true, before = before, after = after)).isEmpty()
        assertThat(detect(enabled = true, fromFeed = false, before = before, after = after)).isEmpty()
    }

    @Test
    fun `a first price has nothing to compare with`() {
        val after = mapOf(PriceFuel.PremiumPetrol to Money(11_000))
        assertThat(detect(enabled = true, fromFeed = true, before = before, after = after)).isEmpty()
    }
}

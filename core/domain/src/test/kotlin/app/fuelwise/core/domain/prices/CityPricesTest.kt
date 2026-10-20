package app.fuelwise.core.domain.prices

import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class CityPricesTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val summarize = SummarizeCityPrices()

    @Test
    fun `latest, change since the previous revision, and future prices ignored`() {
        val prices = listOf(
            price("bengaluru", PriceFuel.Petrol, today.minusDays(10), 10_250),
            price("bengaluru", PriceFuel.Petrol, today.minusDays(1), 10_292),
            price("bengaluru", PriceFuel.Petrol, today.plusDays(1), 10_400),
            price("bengaluru", PriceFuel.Diesel, today, 8_895),
            price("delhi", PriceFuel.Petrol, today, 9_472),
        )
        val s = summarize("bengaluru", prices, today)
        val petrol = s.row(PriceFuel.Petrol)!!
        assertThat(petrol.latest?.price).isEqualTo(Money(10_292))
        assertThat(petrol.change).isEqualTo(Money(42))
        val diesel = s.row(PriceFuel.Diesel)!!
        assertThat(diesel.change).isNull()
        assertThat(s.row(PriceFuel.Cng)!!.latest).isNull()
        assertThat(s.updatedOn).isEqualTo(today)
        assertThat(s.rows.map { it.fuel }).containsExactlyElementsIn(PriceFuel.entries).inOrder()
    }

    @Test
    fun `sparkline carries prices forward over 30 days`() {
        val prices = listOf(
            // Before the window: carried in as the first value.
            price("mumbai", PriceFuel.Cng, today.minusDays(45), 7_500),
            price("mumbai", PriceFuel.Cng, today.minusDays(5), 7_700),
        )
        val line = summarize("mumbai", prices, today).row(PriceFuel.Cng)!!.sparkline
        assertThat(line).hasSize(SummarizeCityPrices.SPARK_DAYS)
        assertThat(line.first()).isEqualTo(Money(7_500))
        assertThat(line.last()).isEqualTo(Money(7_700))
        assertThat(line.count { it == Money(7_700) }).isEqualTo(6)
    }

    @Test
    fun `a single price today has no line`() {
        val line = summarize("delhi", listOf(price("delhi", PriceFuel.Petrol, today, 9_472)), today)
            .row(PriceFuel.Petrol)!!.sparkline
        assertThat(line).isEmpty()
    }

    @Test
    fun `no prices means nothing to show`() {
        val s = summarize("pune", emptyList(), today)
        assertThat(s.hasAny).isFalse()
        assertThat(s.updatedOn).isNull()
    }
}

internal fun price(city: String, fuel: PriceFuel, date: LocalDate, paise: Long) =
    FuelPrice("$city-$fuel-$date", city, fuel, date, Money(paise))

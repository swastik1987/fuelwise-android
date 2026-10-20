package app.fuelwise.core.domain.prices

import app.fuelwise.core.domain.insights.PricePoint
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

class FairPriceTest {
    private val day = LocalDate.of(2026, 10, 16)

    @Test
    fun `fuel follows the tank and the grade`() {
        assertThat(priceFuelFor(EnergyType.PETROL)).isEqualTo(PriceFuel.Petrol)
        assertThat(priceFuelFor(EnergyType.PETROL, FuelGrade.E20)).isEqualTo(PriceFuel.Petrol)
        assertThat(priceFuelFor(EnergyType.PETROL, FuelGrade.Premium)).isEqualTo(PriceFuel.PremiumPetrol)
        assertThat(priceFuelFor(EnergyType.DIESEL)).isEqualTo(PriceFuel.Diesel)
        assertThat(priceFuelFor(EnergyType.CNG)).isEqualTo(PriceFuel.Cng)
        assertThat(priceFuelFor(EnergyType.ELECTRIC)).isNull()
        assertThat(priceFuelFor(EnergyType.LPG)).isNull()
    }

    @Test
    fun `paid above the city price on that day`() {
        val city = listOf(
            price("bengaluru", PriceFuel.Petrol, day.minusDays(3), 10_250),
            price("bengaluru", PriceFuel.Petrol, day, 10_292),
            price("bengaluru", PriceFuel.Petrol, day.plusDays(1), 10_500),
        )
        val fair = FairPrice.of(Money(10_334), day, city)!!
        assertThat(fair.delta).isEqualTo(Money(42))
        assertThat(fair.verdict).isEqualTo(FairPrice.Verdict.Above)
        assertThat(FairPrice.of(Money(10_292), day, city)!!.verdict).isEqualTo(FairPrice.Verdict.Same)
        assertThat(FairPrice.of(Money(10_200), day, city)!!.delta).isEqualTo(Money(-92))
    }

    @Test
    fun `a price set a few days earlier is still in effect, a stale one is not`() {
        val city = listOf(price("delhi", PriceFuel.Diesel, day.minusDays(7), 8_762))
        assertThat(FairPrice.of(Money(8_762), day, city)).isNotNull()
        assertThat(FairPrice.of(Money(8_762), day.plusDays(1), city)).isNull()
        assertThat(FairPrice.of(Money(8_762), day.minusDays(8), city)).isNull()
    }

    @Test
    fun `city price is the default when at least as new as the last fill`() {
        val city = price("bengaluru", PriceFuel.Petrol, day, 10_292)
        val chosen = choosePriceDefault(Money(10_250), day.minusDays(4), city, day)
        assertThat(chosen).isEqualTo(PriceDefault.City(Money(10_292), city))
        assertThat(choosePriceDefault(Money(10_250), day, city, day)).isInstanceOf(PriceDefault.City::class.java)
        assertThat(choosePriceDefault(null, null, city, day)).isInstanceOf(PriceDefault.City::class.java)
    }

    @Test
    fun `last paid wins over an older or future city price`() {
        val old = price("bengaluru", PriceFuel.Petrol, day.minusDays(10), 10_100)
        assertThat(choosePriceDefault(Money(10_250), day.minusDays(2), old, day))
            .isEqualTo(PriceDefault.LastPaid(Money(10_250)))
        val future = price("bengaluru", PriceFuel.Petrol, day.plusDays(1), 10_500)
        assertThat(choosePriceDefault(Money(10_250), day.minusDays(2), future, day))
            .isEqualTo(PriceDefault.LastPaid(Money(10_250)))
        assertThat(choosePriceDefault(null, null, null, day)).isNull()
    }

    @Test
    fun `overlay - the city price in effect on each fill's local day`() {
        val zone = ZoneId.of("Asia/Kolkata")
        fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val city = listOf(
            price("bengaluru", PriceFuel.Petrol, day.minusDays(3), 10_250),
            price("bengaluru", PriceFuel.Petrol, day, 10_292),
        )
        val points = listOf(
            // 00:30 IST on the 16th is still the 15th in UTC: the 16th's price applies.
            PricePoint(at(day, 0) + 30 * 60_000L, Money(10_300)),
            PricePoint(at(day.minusDays(1), 9), Money(10_250)),
            // More than 7 days after the last price: too stale to show.
            PricePoint(at(day.plusDays(8), 9), Money(10_400)),
        )
        assertThat(cityPriceOverlay(points, city, zone))
            .containsExactly(Money(10_292), Money(10_250), null).inOrder()
    }
}

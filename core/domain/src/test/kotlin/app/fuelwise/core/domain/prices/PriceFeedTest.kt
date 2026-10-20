package app.fuelwise.core.domain.prices

import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PriceFeedTest {
    // The example from docs/price-feed.md, trimmed. Illustrative prices, not live data.
    private val sample = """
        {
          "schema": 1,
          "generatedAt": "2026-10-19T06:05:00+05:30",
          "source": "example",
          "futureField": {"ignored": true},
          "cities": [
            {
              "id": "bengaluru", "name": "Bengaluru", "state": "Karnataka",
              "prices": {
                "petrol": {"start": "2026-10-17", "paise": [10250, null, 10292]},
                "diesel": {"start": "2026-10-19", "paise": [8895]},
                "cng": {"start": "2026-10-19", "paise": [8900]},
                "premium_petrol": {"start": "2026-10-19", "paise": [11150]},
                "hydrogen": {"start": "2026-10-19", "paise": [50000]}
              }
            },
            {
              "id": "delhi", "name": "Delhi", "state": "Delhi",
              "prices": {"petrol": {"start": "2026-10-19", "paise": [9472, "bad"]}, "diesel": {"paise": [8762]}}
            },
            {"name": "No id"},
            {
              "id": "mumbai", "name": "Mumbai", "state": "Maharashtra",
              "prices": {"petrol": {"start": "2026-10-19", "paise": [3, 10331, 999999]}}
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses cities, fuels and dated values`() {
        val feed = PriceFeedParser.parse(sample)
        assertThat(feed.schema).isEqualTo(1)
        assertThat(feed.generatedAt).isEqualTo(Instant.parse("2026-10-19T00:35:00Z"))
        assertThat(feed.cities.map { it.city.id }).containsExactly("bengaluru", "delhi", "mumbai").inOrder()
        val blr = feed.cities.first()
        assertThat(blr.city.state).isEqualTo("Karnataka")
        assertThat(blr.prices).containsExactly(
            FeedPrice("bengaluru", PriceFuel.Petrol, LocalDate.of(2026, 10, 17), Money(10_250)),
            FeedPrice("bengaluru", PriceFuel.Petrol, LocalDate.of(2026, 10, 19), Money(10_292)),
            FeedPrice("bengaluru", PriceFuel.Diesel, LocalDate.of(2026, 10, 19), Money(8_895)),
            FeedPrice("bengaluru", PriceFuel.Cng, LocalDate.of(2026, 10, 19), Money(8_900)),
            FeedPrice("bengaluru", PriceFuel.PremiumPetrol, LocalDate.of(2026, 10, 19), Money(11_150)),
        )
    }

    @Test
    fun `bad values and series are skipped, not fatal`() {
        val feed = PriceFeedParser.parse(sample)
        // "bad" is a string and diesel has no start date.
        assertThat(feed.cities[1].prices.map { it.price }).containsExactly(Money(9_472))
        // ₹0.03 and ₹9,999.99 are outside the plausible range; the middle value lands on day 2.
        assertThat(feed.cities[2].prices).containsExactly(
            FeedPrice("mumbai", PriceFuel.Petrol, LocalDate.of(2026, 10, 20), Money(10_331)),
        )
    }

    @Test
    fun `filters to followed cities on the device`() {
        val rows = PriceFeedParser.parse(sample).pricesFor(setOf("delhi", "mumbai", "pune"))
        assertThat(rows.map { it.cityId }.toSet()).containsExactly("delhi", "mumbai")
    }

    @Test
    fun `rejects non-JSON, a missing schema and a newer schema`() {
        assertThrows<PriceFeedFormatException> { PriceFeedParser.parse("<html>") }
        assertThrows<PriceFeedFormatException> { PriceFeedParser.parse("[]") }
        assertThrows<PriceFeedFormatException> { PriceFeedParser.parse("""{"cities": []}""") }
        assertThrows<PriceFeedFormatException> { PriceFeedParser.parse("""{"schema": 2, "cities": []}""") }
        assertThat(PriceFeedParser.parse("""{"schema": 1, "cities": []}""").cities).isEmpty()
    }

    @Test
    fun `due once a day after 06_30 IST`() {
        val before = ist(2026, 10, 19, 6, 29)
        val opens = ist(2026, 10, 19, 6, 30)
        val later = ist(2026, 10, 19, 21, 0)
        assertThat(PriceFeedSchedule.isDue(before, lastFetchAt = null)).isFalse()
        assertThat(PriceFeedSchedule.isDue(opens, lastFetchAt = null)).isTrue()
        // Fetched yesterday evening: due again once today's window opens.
        val yesterday = ist(2026, 10, 18, 20, 0)
        assertThat(PriceFeedSchedule.isDue(before, yesterday)).isFalse()
        assertThat(PriceFeedSchedule.isDue(later, yesterday)).isTrue()
        // Already fetched this morning: not again today.
        assertThat(PriceFeedSchedule.isDue(later, ist(2026, 10, 19, 7, 0))).isFalse()
        // A fetch before the window (yesterday's file) doesn't count for today.
        assertThat(PriceFeedSchedule.isDue(later, ist(2026, 10, 19, 6, 0))).isTrue()
    }

    @Test
    fun `next window is in IST whatever the phone zone`() {
        assertThat(PriceFeedSchedule.delayUntilNextWindow(ist(2026, 10, 19, 5, 30))).isEqualTo(Duration.ofHours(1))
        assertThat(PriceFeedSchedule.delayUntilNextWindow(ist(2026, 10, 19, 6, 30))).isEqualTo(Duration.ofDays(1))
        assertThat(PriceFeedSchedule.delayUntilNextWindow(ist(2026, 10, 19, 22, 30), Duration.ofMinutes(12)))
            .isEqualTo(Duration.ofHours(8).plusMinutes(12))
    }

    @Test
    fun `station search names the fuel and carries no location`() {
        assertThat(StationSearch.query(EnergyType.CNG)).isEqualTo("CNG station near me")
        assertThat(StationSearch.query(null)).isEqualTo("petrol pump near me")
        val url = StationSearch.mapsUrl(EnergyType.CNG)
        assertThat(url).isEqualTo("https://www.google.com/maps/search/?api=1&query=CNG+station+near+me")
    }

    @Test
    fun `city catalog reads ids, states and popular chips`() {
        val catalog = CityCatalog.parse(
            """
            {"version": 3, "cities": [
              {"id": "bengaluru", "name": "Bengaluru", "state": "Karnataka", "popular": true},
              {"id": "mysuru", "name": "Mysuru", "state": "Karnataka"},
              {"id": "", "name": "Blank"},
              {"id": "bengaluru", "name": "Duplicate"}
            ]}
            """.trimIndent(),
        )
        assertThat(catalog.version).isEqualTo(3)
        assertThat(catalog.cities.map { it.name }).containsExactly("Bengaluru", "Mysuru").inOrder()
        assertThat(catalog.popular.map { it.id }).containsExactly("bengaluru")
        assertThat(catalog.search("karna").map { it.id }).containsExactly("bengaluru", "mysuru")
        assertThat(catalog.byId("mysuru")?.state).isEqualTo("Karnataka")
        assertThat(CityCatalog.parse("nope")).isEqualTo(CityCatalog.EMPTY)
    }

    private fun ist(y: Int, m: Int, d: Int, h: Int, min: Int): Instant =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, PriceFeedSchedule.IST).toInstant()
}

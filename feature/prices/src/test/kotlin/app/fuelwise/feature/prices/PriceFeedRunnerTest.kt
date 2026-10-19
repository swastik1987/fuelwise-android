package app.fuelwise.feature.prices

import app.fuelwise.core.data.repository.PriceSettings
import app.fuelwise.core.domain.prices.DetectPriceAlerts
import app.fuelwise.core.domain.prices.PriceChange
import app.fuelwise.core.domain.prices.PriceFeedSchedule
import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.FuelPriceOrigin
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import app.fuelwise.core.testing.FakeFuelPriceRepository
import app.fuelwise.core.testing.FakePriceSettingsRepository
import app.fuelwise.feature.prices.feed.FeedOutcome
import app.fuelwise.feature.prices.feed.PriceFeedEndpoint
import app.fuelwise.feature.prices.feed.PriceFeedRunner
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The feed run (PRD F-09.2, F-09.5) against fakes; the example URL never leaves the test. */
class PriceFeedRunnerTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val morning = ZonedDateTime.of(2026, 10, 19, 7, 0, 0, 0, PriceFeedSchedule.IST).toInstant()
    private val feed = """
        {"schema": 1, "cities": [
          {"id": "bengaluru", "name": "Bengaluru", "state": "Karnataka", "prices": {
            "petrol": {"start": "2026-10-18", "paise": [10292, 10412]},
            "diesel": {"start": "2026-10-19", "paise": [8895]}
          }},
          {"id": "delhi", "name": "Delhi", "state": "Delhi", "prices": {"petrol": {"start": "2026-10-19", "paise": [9472]}}},
          {"id": "chennai", "name": "Chennai", "state": "Tamil Nadu", "prices": {"petrol": {"start": "2026-10-19", "paise": [10075]}}}
        ]}
    """.trimIndent()

    private val prices = FakeFuelPriceRepository(
        listOf(FuelPrice("old", "bengaluru", PriceFuel.Petrol, today.minusDays(1), Money(10_292), FuelPriceOrigin.Feed)),
    )
    private val alerts = mutableListOf<Pair<PriceChange, String>>()
    private val fetched = mutableListOf<String>()

    private fun runner(settings: PriceSettings, url: String? = TEST_URL) = PriceFeedRunner(
        endpoint = PriceFeedEndpoint(url),
        settings = FakePriceSettingsRepository(settings),
        prices = prices,
        fetcher = { fetched += it; feed },
        alerts = { change, city -> alerts += change to city },
        detect = DetectPriceAlerts(),
    )

    private val on = PriceSettings(homeCityId = "bengaluru", pinnedCityIds = listOf("delhi"), feedEnabled = true)

    @Test
    fun shippedConfigHasNoUrlSoNothingIsFetched() = runTest {
        assertThat(PriceFeedEndpoint.CONFIGURED.url).isNull()
        assertThat(runner(on, url = null).run(morning)).isEqualTo(FeedOutcome.Disabled)
        assertThat(runner(on.copy(feedEnabled = false)).run(morning)).isEqualTo(FeedOutcome.Disabled)
        assertThat(fetched).isEmpty()
    }

    @Test
    fun keepsOnlyFollowedCitiesAndAlertsOnARupeeMove() = runTest {
        val r = runner(on.copy(alerts = true))
        assertThat(r.run(morning)).isEqualTo(FeedOutcome.Updated)
        assertThat(fetched).containsExactly(TEST_URL)
        assertThat(prices.prices.value.map { it.cityId }.toSet()).containsExactly("bengaluru", "delhi")
        // Petrol went ₹102.92 → ₹104.12; diesel had no earlier price, so no alert for it.
        assertThat(alerts.map { it.first.fuel to it.second }).containsExactly(PriceFuel.Petrol to "Bengaluru")
        assertThat(alerts.single().first.delta).isEqualTo(Money(120))
    }

    @Test
    fun alertsOffStillUpdatesQuietly() = runTest {
        assertThat(runner(on).run(morning)).isEqualTo(FeedOutcome.Updated)
        assertThat(alerts).isEmpty()
    }

    @Test
    fun onceADayAfter0630Ist() = runTest {
        val early = ZonedDateTime.of(2026, 10, 19, 6, 0, 0, 0, PriceFeedSchedule.IST).toInstant()
        assertThat(runner(on).run(early)).isEqualTo(FeedOutcome.NotDue)
        val fetchedThisMorning = on.copy(lastFeedFetchUtcMs = morning.toEpochMilli())
        assertThat(runner(fetchedThisMorning).run(morning.plusSeconds(3_600))).isEqualTo(FeedOutcome.NotDue)
        assertThat(fetched).isEmpty()
    }

    @Test
    fun aTypedPriceIsNeverOverwritten() = runTest {
        prices.saveManual("delhi", PriceFuel.Petrol, today, Money(9_500))
        runner(on).run(morning)
        val delhi = prices.prices.value.single { it.cityId == "delhi" }
        assertThat(delhi.price).isEqualTo(Money(9_500))
        assertThat(delhi.origin).isEqualTo(FuelPriceOrigin.Manual)
    }

    @Test
    fun noCityNoFetchAndABadFileIsNotRetried() = runTest {
        assertThat(runner(PriceSettings(feedEnabled = true)).run(morning)).isEqualTo(FeedOutcome.NothingFollowed)
        val bad = PriceFeedRunner(
            PriceFeedEndpoint(TEST_URL),
            FakePriceSettingsRepository(on),
            prices,
            { "<html>" },
            { _, _ -> },
            DetectPriceAlerts(),
        )
        assertThat(bad.run(morning)).isEqualTo(FeedOutcome.BadFile)
        val offline = PriceFeedRunner(
            PriceFeedEndpoint(TEST_URL),
            FakePriceSettingsRepository(on),
            prices,
            { null },
            { _, _ -> },
            DetectPriceAlerts(),
        )
        assertThat(offline.run(morning)).isEqualTo(FeedOutcome.Failed)
    }

    private companion object {
        // Reserved example domain (RFC 2606): tests only, never a real feed host.
        const val TEST_URL = "https://feed.example/prices.json"
    }
}

package app.fuelwise.feature.prices.feed

import app.fuelwise.core.data.repository.FuelPriceRepository
import app.fuelwise.core.data.repository.PriceSettingsRepository
import app.fuelwise.core.domain.prices.DetectPriceAlerts
import app.fuelwise.core.domain.prices.PriceChange
import app.fuelwise.core.domain.prices.PriceFeedFormatException
import app.fuelwise.core.domain.prices.PriceFeedParser
import app.fuelwise.core.domain.prices.PriceFeedSchedule
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Where the national price file lives. Null until PRD D2 (source) and D3 (host) are decided: no URL ships in the code
 * or config, and nothing is fetched without one.
 */
data class PriceFeedEndpoint(val url: String?) {
    companion object {
        /** Deliberately null (owner decision, 19 Oct 2026). Setting it needs approval and an update to PRD §9. */
        val CONFIGURED = PriceFeedEndpoint(url = null)
    }
}

/** Downloads the whole file; no location, account or device identifier is sent (CLAUDE.md rule 5). */
fun interface PriceFeedFetcher {
    /** The file's text, or null when offline or the server failed. */
    suspend fun fetch(url: String): String?
}

/** Posts a price alert (core:notifications in the app, a list in tests). */
fun interface PriceAlertSink {
    fun notify(change: PriceChange, cityName: String)
}

enum class FeedOutcome {
    /** Flag off or no URL: nothing happened. */
    Disabled,

    /** Today's window hasn't opened, or today's file was already read. */
    NotDue,

    /** No home or pinned city, so there is nothing to keep. */
    NothingFollowed,
    Updated,

    /** Offline or a server error: worth a retry. */
    Failed,

    /** The file wasn't in the documented format: retrying won't help. */
    BadFile,
}

/**
 * One feed run (PRD F-09.2): fetch once a day after 06:30 IST, keep only the followed cities, write them to the
 * same table as typed prices, and raise opt-in alerts for home-city moves of ₹1 or more (F-09.5).
 */
class PriceFeedRunner @Inject constructor(
    private val endpoint: PriceFeedEndpoint,
    private val settings: PriceSettingsRepository,
    private val prices: FuelPriceRepository,
    private val fetcher: PriceFeedFetcher,
    private val alerts: PriceAlertSink,
    private val detect: DetectPriceAlerts,
) {
    // Guard clauses, one per reason a run stops early; nesting them would read worse.
    @Suppress("ReturnCount")
    suspend fun run(now: Instant): FeedOutcome {
        val s = settings.settings.first()
        val url = endpoint.url
        if (!s.feedEnabled || url.isNullOrBlank()) return FeedOutcome.Disabled
        if (!PriceFeedSchedule.isDue(now, s.lastFeedFetchUtcMs?.let(Instant::ofEpochMilli))) return FeedOutcome.NotDue
        val followed = s.followedCityIds
        if (followed.isEmpty()) return FeedOutcome.NothingFollowed

        val text = fetcher.fetch(url) ?: return FeedOutcome.Failed
        val feed = try {
            PriceFeedParser.parse(text)
        } catch (_: PriceFeedFormatException) {
            return FeedOutcome.BadFile
        }
        val today = now.atZone(PriceFeedSchedule.IST).toLocalDate()
        val home = s.homeCityId
        val before = home?.let { inEffect(it, today) }.orEmpty()
        prices.applyFeed(feed.pricesFor(followed))
        settings.markFeedFetched(now.toEpochMilli())
        if (home != null) {
            val cityName = feed.cities.firstOrNull { it.city.id == home }?.city?.name ?: home
            detect(s.alerts, fromFeed = true, before = before, after = inEffect(home, today))
                .forEach { alerts.notify(it, cityName) }
        }
        return FeedOutcome.Updated
    }

    private suspend fun inEffect(cityId: String, today: LocalDate): Map<PriceFuel, Money> =
        PriceFuel.entries.mapNotNull { fuel ->
            prices.latestOnOrBefore(cityId, fuel, today)?.let { fuel to it.price }
        }.toMap()
}

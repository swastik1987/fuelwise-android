package app.fuelwise.feature.prices.feed

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.fuelwise.core.data.repository.PriceSettingsRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.prices.PriceChange
import app.fuelwise.core.domain.prices.PriceFeedSchedule
import app.fuelwise.core.notifications.Notifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Runs [PriceFeedRunner]; a failed download retries with backoff, everything else waits for tomorrow. */
@HiltWorker
class PriceFeedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: PriceFeedRunner,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (runner.run(clock.instant())) {
        FeedOutcome.Failed -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        else -> Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

/**
 * Keeps WorkManager in step with the feed flag: a unique periodic job while the flag is on and a URL exists, none
 * otherwise. The job wakes a few times a day; [PriceFeedSchedule] lets at most one run a day fetch, after 06:30 IST.
 */
@Singleton
class PriceFeedScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: PriceSettingsRepository,
    private val endpoint: PriceFeedEndpoint,
    private val clock: Clock,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.settings.map { it.feedEnabled && !endpoint.url.isNullOrBlank() }
                .distinctUntilChanged()
                .collect { enabled -> if (enabled) enqueue() else cancel() }
        }
    }

    private fun enqueue() {
        val jitter = Duration.ofMinutes(Random.nextLong(JITTER_MINUTES))
        val request = PeriodicWorkRequestBuilder<PriceFeedWorker>(REPEAT_HOURS, TimeUnit.HOURS)
            .setInitialDelay(
                PriceFeedSchedule.delayUntilNextWindow(clock.instant(), jitter).toMillis(),
                TimeUnit.MILLISECONDS,
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    private companion object {
        const val WORK = "price-feed"
        const val REPEAT_HOURS = 8L
        const val JITTER_MINUTES = 90L
        const val BACKOFF_MINUTES = 30L
    }
}

/**
 * Plain GET of the whole file: no query, no cookies, no custom headers, so the request carries no location or
 * identifier. A gzip body (a `.json.gz` file) is unwrapped here; `Content-Encoding: gzip` OkHttp unwraps itself.
 */
internal class OkHttpPriceFeedFetcher @Inject constructor(
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : PriceFeedFetcher {
    private val http = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(url: String): String? = withContext(io) {
        try {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body.bytes()
                if (bytes.size > MAX_BYTES) return@use null
                decode(bytes)
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun decode(bytes: ByteArray): String {
        val gzip = bytes.size > 2 && bytes[0] == GZIP_0 && bytes[1] == GZIP_1
        return if (gzip) {
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
        } else {
            bytes.decodeToString()
        }
    }

    private companion object {
        const val TIMEOUT_S = 30L
        const val MAX_BYTES = 16 * 1024 * 1024
        const val GZIP_0 = 0x1f.toByte()
        const val GZIP_1 = 0x8b.toByte()
    }
}

internal class NotifierPriceAlertSink @Inject constructor(private val notifier: Notifier) : PriceAlertSink {
    override fun notify(change: PriceChange, cityName: String) = notifier.priceChange(change, cityName)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PriceFeedModule {
    @Binds
    fun fetcher(impl: OkHttpPriceFeedFetcher): PriceFeedFetcher

    @Binds
    fun alerts(impl: NotifierPriceAlertSink): PriceAlertSink

    companion object {
        @Provides
        fun endpoint(): PriceFeedEndpoint = PriceFeedEndpoint.CONFIGURED
    }
}

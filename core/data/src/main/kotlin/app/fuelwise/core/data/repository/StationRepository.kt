package app.fuelwise.core.data.repository

import android.content.Context
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.station.StationCodec
import app.fuelwise.core.domain.station.StationIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The offline station list (PRD F-21.2, D16): OpenStreetMap fuel and charging stations in India, bundled in the APK
 * and refreshed with app updates. Matching happens on the phone; no coordinates ever leave it (CLAUDE.md rule 5).
 * Data © OpenStreetMap contributors, ODbL.
 */
interface StationRepository {
    /** Stations within [radiusM] of the point, nearest first, with their distance in metres. */
    suspend fun near(lat: Double, lng: Double, radiusM: Double): List<Pair<Station, Double>>

    /** How many stations the bundled list has (0 if it's missing or unreadable). */
    suspend fun count(): Int
}

/** Reads the bundled file once, on first use, into an in-memory grid (a few MB for all of India). */
@Singleton
internal class AssetStationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : StationRepository {
    private val mutex = Mutex()
    private var index: StationIndex? = null

    override suspend fun near(lat: Double, lng: Double, radiusM: Double) = index().near(lat, lng, radiusM)

    override suspend fun count(): Int = index().size

    private suspend fun index(): StationIndex = mutex.withLock {
        index ?: withContext(io) { StationIndex(load()) }.also { index = it }
    }

    private fun load(): List<Station> = try {
        context.assets.open(STATIONS_FILE).bufferedReader().useLines { lines ->
            lines.mapNotNull(StationCodec::decodeLine).toList()
        }
    } catch (_: IOException) {
        // A build without the file (or a corrupt one) still runs; fill-up detection just finds nothing.
        emptyList()
    }

    private companion object {
        // The repo keeps india.tsv.gz; the Android build gunzips .gz assets and drops the suffix, and the APK's own zip
        // compression keeps it small.
        const val STATIONS_FILE = "stations/india.tsv"
    }
}

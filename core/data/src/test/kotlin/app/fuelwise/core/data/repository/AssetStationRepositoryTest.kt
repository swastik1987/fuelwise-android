package app.fuelwise.core.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The bundled OpenStreetMap list (PRD F-21.2) loads from the APK's assets and answers lookups offline. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AssetStationRepositoryTest {
    private val stations = AssetStationRepository(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined)

    @Test
    fun bundledListLoadsAndFindsStationsNearby() = runTest {
        // Tens of thousands across India; the exact count changes with each refresh.
        assertThat(stations.count()).isGreaterThan(10_000)
        // Central Delhi always has some within 2 km.
        val near = stations.near(28.6315, 77.2167, radiusM = 2_000.0)
        assertThat(near).isNotEmpty()
        assertThat(near.map { it.second }).isInOrder()
        assertThat(near.all { it.second <= 2_000.0 }).isTrue()
    }
}

package app.fuelwise.core.datastore

import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PreferencesDataSourceTest {
    private val dir: File = Files.createTempDirectory("prefs").toFile()

    private fun TestScope.newSource(): PreferencesDataSource {
        var counter = 0
        return PreferencesDataSource(
            store = DataStoreFactory.create(
                serializer = UserPreferencesSerializer(),
                scope = backgroundScope,
                produceFile = { File(dir, "prefs.pb") },
            ),
            ids = { "device-${++counter}" },
        )
    }

    @Test
    fun `first run defaults - monthly recap on, crash reports off, automatic trips available but not on`() = runTest {
        val prefs = newSource().preferences.first()
        assertThat(prefs.monthlyRecap).isTrue()
        assertThat(prefs.crashReports).isFalse()
        assertThat(prefs.onboardingCompleted).isFalse()
        assertThat(prefs.featureFlags.automaticTripsOff).isFalse()
        assertThat(prefs.autoTrips.enabled).isFalse()
    }

    @Test
    fun `device id is created once and then stable`() = runTest {
        val source = newSource()
        val ids = List(5) { async { source.deviceId() } }.awaitAll()
        assertThat(ids.toSet()).containsExactly("device-1")
        assertThat(source.deviceId()).isEqualTo("device-1")
    }

    @Test
    fun `updates persist`() = runTest {
        val source = newSource()
        source.update {
            onboardingCompleted = true
            homeCityId = "bengaluru"
        }
        val prefs = source.preferences.first()
        assertThat(prefs.onboardingCompleted).isTrue()
        assertThat(prefs.homeCityId).isEqualTo("bengaluru")
    }
}

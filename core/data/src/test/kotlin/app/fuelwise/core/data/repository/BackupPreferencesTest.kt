package app.fuelwise.core.data.repository

import androidx.datastore.core.DataStoreFactory
import app.fuelwise.core.datastore.EfficiencyUnitPref
import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.UserPreferencesSerializer
import app.fuelwise.core.datastore.unitPreferences
import app.fuelwise.core.domain.id.UuidV7Generator
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Preferences that travel in a backup (PRD F-14.4) come back on another device; device-only ones don't. */
class BackupPreferencesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-20T04:30:00Z"), ZoneOffset.UTC)
    private val ids = UuidV7Generator(clock)
    private val dir: File = Files.createTempDirectory("backup-prefs").toFile()

    private fun source(scope: CoroutineScope, name: String) = PreferencesDataSource(
        DataStoreFactory.create(UserPreferencesSerializer(), scope = scope) { File(dir, "$name.pb") },
        ids,
    )

    @Test
    fun preferencesRoundTripToAnotherDevice() = runTest {
        val phoneA = source(backgroundScope, "a")
        phoneA.update {
            homeCityId = "bengaluru"
            pinnedCityIds.addAll(listOf("delhi", "mumbai"))
            priceAlerts = true
            homeTariffPaise = 750
            units = unitPreferences { efficiency = EfficiencyUnitPref.EFFICIENCY_UNIT_QUANTITY_PER_100_DISTANCE }
            scanPhotoOptOut = true
            businessRatePaisePerKm = 1_200
            upiVpa = "asha@okaxis"
            appLock = true
        }
        // Snapshot JSON keeps whole numbers as Long, which is what export hands over here.
        val exported = DataStoreBackupSettingsRepository(phoneA).exportPreferences()

        val phoneB = source(backgroundScope, "b")
        DataStoreBackupSettingsRepository(phoneB).importPreferences(exported)
        val restored = phoneB.preferences.first()

        assertThat(restored.homeCityId).isEqualTo("bengaluru")
        assertThat(restored.pinnedCityIdsList).containsExactly("delhi", "mumbai").inOrder()
        assertThat(restored.priceAlerts).isTrue()
        assertThat(restored.homeTariffPaise).isEqualTo(750)
        assertThat(restored.units.efficiency).isEqualTo(EfficiencyUnitPref.EFFICIENCY_UNIT_QUANTITY_PER_100_DISTANCE)
        assertThat(restored.scanPhotoOptOut).isTrue()
        assertThat(restored.businessRatePaisePerKm).isEqualTo(1_200)
        assertThat(restored.upiVpa).isEqualTo("asha@okaxis")
        // The app lock is this device's choice: never restored.
        assertThat(restored.appLock).isFalse()
    }
}

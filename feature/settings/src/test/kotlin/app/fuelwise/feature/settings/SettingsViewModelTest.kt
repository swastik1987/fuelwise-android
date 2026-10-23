package app.fuelwise.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.fuelwise.core.data.repository.BackupSettingsRepository
import app.fuelwise.core.data.repository.LocalDataReset
import app.fuelwise.core.data.repository.ThemeMode
import app.fuelwise.core.domain.trip.AutoTripPause
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.domain.units.EfficiencyUnit
import app.fuelwise.core.model.BackupError
import app.fuelwise.core.model.BackupSettings
import app.fuelwise.core.model.Money
import app.fuelwise.core.testing.FakeAutoTripSettingsRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.feature.settings.units.UnitsViewModel
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val userData = FakeUserDataRepository()
    private val vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari, SampleVehicles.nexonEv))
    private val backupSettings = FakeBackupSettings()
    private val eraser = FakeEraser()
    private var resets = 0
    private val reset = LocalDataReset { resets++ }

    private val autoTrips = FakeAutoTripSettingsRepository()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 23, 18, 30).atZone(zone).toInstant()
    private val clock = Clock.fixed(now, zone)

    private fun viewModel() =
        SettingsViewModel(userData, vehicles, backupSettings, eraser, reset, autoTrips, clock, zone)

    @Test
    fun automaticTripsPauseResumeAndTurnOff() = runTest {
        val vm = viewModel()
        autoTrips.setEnabled(true)
        vm.pauseAutoTrips(AutoTripPause.OneHour)
        assertThat(autoTrips.settings.value.pausedUntilUtcMs).isEqualTo(now.toEpochMilli() + 3_600_000)
        assertThat(vm.state.first { !it.loading }.autoTrips?.pausedUntilUtcMs)
            .isEqualTo(now.toEpochMilli() + 3_600_000)
        vm.resumeAutoTrips()
        assertThat(autoTrips.settings.value.pausedUntilUtcMs).isNull()
        vm.turnOffAutoTrips()
        assertThat(autoTrips.settings.value.enabled).isFalse()
    }

    @Test
    fun choicesAreSavedToPreferences() = runTest {
        val vm = viewModel()
        vm.setThemeMode(ThemeMode.Dark)
        vm.setDynamicColor(true)
        vm.setHideAmounts(true)
        vm.setHomeTariff(Money(820))
        vm.setUpi("asha.k@okaxis", "Asha K")
        val data = userData.userData.value
        assertThat(data.themeMode).isEqualTo(ThemeMode.Dark)
        assertThat(data.dynamicColor).isTrue()
        assertThat(data.hideAmounts).isTrue()
        assertThat(data.homeTariff).isEqualTo(Money(820))
        assertThat(data.upiVpa).isEqualTo("asha.k@okaxis")
    }

    @Test
    fun aZeroTariffClearsIt() = runTest {
        val vm = viewModel()
        vm.setHomeTariff(Money(820))
        vm.setHomeTariff(Money(0))
        assertThat(userData.userData.value.homeTariff).isNull()
    }

    @Test
    fun appLockKeepsItsTimeoutAcrossToggles() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.setAppLock(true)
            vm.setAppLockTimeout(5)
            assertThat(userData.userData.value.appLockTimeoutMinutes).isEqualTo(5)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(userData.userData.value.appLock).isTrue()
    }

    @Test
    fun deleteAllWipesLocalDataAndForgetsTheBackupKey() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.deleteAllData(alsoDrive = false)
            assertThat(awaitItem()).isEqualTo(SettingsEvent.DataDeleted)
        }
        assertThat(resets).isEqualTo(1)
        assertThat(eraser.driveDeletes).isEqualTo(0)
        assertThat(eraser.keyForgotten).isTrue()
    }

    @Test
    fun driveFailureDeletesNothing() = runTest {
        eraser.driveResult = BackupError.Offline
        val vm = viewModel()
        vm.events.test {
            vm.deleteAllData(alsoDrive = true)
            assertThat(awaitItem()).isEqualTo(SettingsEvent.DriveDeleteFailed(BackupError.Offline))
        }
        assertThat(resets).isEqualTo(0)
        assertThat(eraser.keyForgotten).isFalse()
    }

    @Test
    fun driveBackupsGoFirstWhenAsked() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.deleteAllData(alsoDrive = true)
            assertThat(awaitItem()).isEqualTo(SettingsEvent.DataDeleted)
        }
        assertThat(eraser.driveDeletes).isEqualTo(1)
        assertThat(resets).isEqualTo(1)
    }

    @Test
    fun unitsDefaultAndPerVehicleOverride() = runTest {
        val app = UnitsViewModel(SavedStateHandle(mapOf("vehicleId" to null)), userData, vehicles)
        app.state.first { !it.loading }
        app.update { it.copy(efficiency = EfficiencyUnit.QUANTITY_PER_100_DISTANCE) }
        assertThat(userData.userData.value.units.default.efficiency)
            .isEqualTo(EfficiencyUnit.QUANTITY_PER_100_DISTANCE)

        val nexon = UnitsViewModel(SavedStateHandle(mapOf("vehicleId" to "nexon")), userData, vehicles)
        assertThat(nexon.state.first { !it.loading }.vehicle?.override).isNull()
        nexon.setOwnUnits(true)
        nexon.state.first { it.vehicle?.override != null }
        nexon.update { it.copy(efficiency = EfficiencyUnit.DISTANCE_PER_QUANTITY) }
        val units = userData.userData.value.units
        assertThat(units.forVehicle("nexon")).isEqualTo(DisplayUnits.INDIA)
        assertThat(units.forVehicle("safari").efficiency).isEqualTo(EfficiencyUnit.QUANTITY_PER_100_DISTANCE)

        nexon.setOwnUnits(false)
        assertThat(userData.userData.value.units.perVehicle).doesNotContainKey("nexon")
    }

    private class FakeEraser : BackupEraser {
        var driveResult: BackupError? = null
        var driveDeletes = 0
        var keyForgotten = false

        override suspend fun deleteDriveBackups(): BackupError? {
            driveDeletes++
            return driveResult
        }

        override suspend fun forgetKey() {
            keyForgotten = true
        }
    }

    private class FakeBackupSettings : BackupSettingsRepository {
        private val state = MutableStateFlow(BackupSettings(accountEmail = "owner@example.com"))
        override val settings = state

        override suspend fun update(transform: (BackupSettings) -> BackupSettings) = state.update(transform)

        override suspend fun exportPreferences(): Map<String, Any> = emptyMap()

        override suspend fun importPreferences(values: Map<String, Any?>) = Unit
    }
}

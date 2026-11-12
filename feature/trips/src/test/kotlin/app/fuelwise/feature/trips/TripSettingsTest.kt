package app.fuelwise.feature.trips

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.repository.CarLink
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.trip.AutoTripPause
import app.fuelwise.core.domain.trip.RouteRetention
import app.fuelwise.core.location.CarLinker
import app.fuelwise.core.testing.FakeAutoTripSettingsRepository
import app.fuelwise.core.testing.FakeLocationPrivacyRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Trip settings inside Trips (moved from Settings, 12 Nov 2026): view model, primary flow and screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class TripSettingsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 23, 18, 30).atZone(zone).toInstant()
    private val clock = Clock.fixed(now, zone)
    private val autoTrips = FakeAutoTripSettingsRepository()
    private val privacy = FakeLocationPrivacyRepository()
    private val vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari, SampleVehicles.activa))
    private val linker = object : CarLinker {
        override suspend fun linkCar(vehicleId: String, link: CarLink) = autoTrips.setCarLink(vehicleId, link)

        override suspend fun unlinkCar(vehicleId: String) = autoTrips.setCarLink(vehicleId, null)
    }

    private fun viewModel() = TripSettingsViewModel(autoTrips, privacy, vehicles, linker, clock, zone)

    @Test
    fun routeRetentionAndLocationHistory() = runTest {
        val vm = viewModel()
        privacy.addPrivatePlace("Home", 12.91, 77.64)
        vm.setRouteRetention(RouteRetention.Days30)
        val state = vm.state.first { !it.loading && it.privatePlaceCount == 1 }
        assertThat(state.routeRetention).isEqualTo(RouteRetention.Days30)
        vm.deleteLocationHistory()
        assertThat(privacy.historyDeletes).isEqualTo(1)
        assertThat(vm.state.first { !it.loading && it.privatePlaceCount == 0 }.privatePlaceCount).isEqualTo(0)
    }

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
    fun carBluetoothListsCarsOnlyAndLinksPerVehicle() = runTest {
        val vm = viewModel()
        assertThat(vm.state.first { !it.loading }.cars.map { it.vehicleId }).containsExactly(SampleVehicles.safari.id)
        vm.linkCar(SampleVehicles.safari.id, CarLink("AA:BB:CC:DD:EE:FF", 7, "Safari Audio"))
        val linked = vm.state.first { s -> s.cars.any { it.linked } }.cars.single()
        assertThat(linked.carName).isEqualTo("Safari Audio")
        vm.unlinkCar(SampleVehicles.safari.id)
        assertThat(vm.state.first { s -> s.cars.none { it.linked } }.cars.single().linked).isFalse()
    }

    @Test
    fun rowsLeadToSetupAccessAndPrivatePlaces() {
        val taps = mutableListOf<String>()
        val actions = TripSettingsActions(
            onAutoTripSetup = { taps += "setup" },
            onLocationAccess = { taps += "access" },
            onPrivatePlaces = { taps += "places" },
        )
        compose.setContent { FuelwiseTheme { TripSettingsContent(offState, LocationAccess.WhileUsing, actions) } }
        compose.onNodeWithText("Automatic trips").performClick()
        compose.onNodeWithText("While using the app").performClick()
        compose.onNodeWithText("Private places").performClick()
        assertThat(taps).containsExactly("setup", "access", "places").inOrder()
    }

    private val offState = TripSettingsUiState(loading = false, autoTrips = AutoTripsUi(false, false, null))

    private val onState = TripSettingsUiState(
        loading = false,
        autoTrips = AutoTripsUi(enabled = true, oneTap = false, pausedUntilUtcMs = null),
        cars = listOf(
            CarRow(SampleVehicles.safari.id, SampleVehicles.safari.name, linked = true, carName = "Safari Audio"),
            CarRow(SampleVehicles.ertiga.id, SampleVehicles.ertiga.name, linked = false, carName = null),
        ),
        routeRetention = RouteRetention.Days90,
        privatePlaceCount = 2,
    )

    private val withCars = TripSettingsActions(onLinkCar = {})

    @Test fun onLight() = capture("trip_settings_light", false) {
        TripSettingsContent(onState, LocationAccess.AllTheTime, withCars)
    }

    @Test fun onDark() = capture("trip_settings_dark", true) {
        TripSettingsContent(onState, LocationAccess.AllTheTime, withCars)
    }

    @Test fun offLight() = capture("trip_settings_off_light", false) {
        TripSettingsContent(offState, LocationAccess.Off, withCars)
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

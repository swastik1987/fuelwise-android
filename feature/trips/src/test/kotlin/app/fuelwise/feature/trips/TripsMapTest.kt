package app.fuelwise.feature.trips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.trip.HeatPoint
import app.fuelwise.core.domain.trip.VisitedPlace
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.core.ui.map.LocalMapsEnabled
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

/** The consolidated trips map (PRD F-07.7, free in R1 since 12 Nov 2026): view model and screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class TripsMapTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 11, 12, 10, 0).atZone(zone).toInstant()
    private val clock = Clock.fixed(now, zone)
    private val trips = FakeTripRepository()

    private fun point(lat: Double, lng: Double) = TrackPoint(0, lat, lng, 8f, null, 0)

    // Home (HSR Layout) to the office (Whitefield) and back: about 15 km each way.
    private val toWork = listOf(point(12.9100, 77.6400), point(12.9500, 77.7000), point(12.9780, 77.7500))
    private val home = listOf(point(12.9780, 77.7500), point(12.9500, 77.7000), point(12.9101, 77.6401))

    private suspend fun recorded(id: String, route: List<TrackPoint>, monthsAgo: Long, source: TripSource) {
        val start = now.atZone(zone).minusMonths(monthsAgo).toInstant().toEpochMilli()
        val trip = Trip(
            id = id,
            vehicleId = "safari",
            startUtcMs = start,
            zoneId = zone.id,
            distanceKm = 15.0,
            purpose = TripPurpose.Commute,
            endUtcMs = start + 1_800_000,
            source = source,
        )
        trips.finishRecording(trip, route)
    }

    private fun viewModel(refuels: List<Refuel> = emptyList()) = TripsMapViewModel(
        trips,
        FakeEntryRepository(refuels = refuels),
        FakeVehicleRepository(listOf(SampleVehicles.safari)),
        clock,
        zone,
        main.dispatcher,
    )

    @Test
    fun routesPlacesAndFillUpsWithinThePeriod() = runTest {
        recorded("a", toWork, monthsAgo = 1, source = TripSource.Auto)
        recorded("b", home, monthsAgo = 1, source = TripSource.Gps)
        recorded("old", toWork, monthsAgo = 20, source = TripSource.Auto)
        // A typed trip has no route to draw.
        trips.save(Trip("m", "safari", now.toEpochMilli(), zone.id, 4.0, TripPurpose.Personal))
        val placed = SampleEntries.safariRefuels.last().copy(id = "f1", lat = 12.9352, lng = 77.6245)
        val vm = viewModel(SampleEntries.safariRefuels.dropLast(1) + placed)

        val year = vm.state.first { !it.loading }
        assertThat(year.routes).isEqualTo(2)
        assertThat(year.heat.size).isGreaterThan(toWork.size * 2)
        // Home and the office are each an end twice.
        assertThat(year.places.map { it.visits }).containsExactly(2, 2)
        assertThat(year.fillUps.single()).isEqualTo(FillUpPin("f1", 12.9352, 77.6245, EnergyType.DIESEL))

        vm.selectPeriod(MapPeriod.All)
        assertThat(vm.state.first { it.period == MapPeriod.All }.routes).isEqualTo(3)
    }

    @Test
    fun nothingRecordedIsEmpty() = runTest {
        assertThat(viewModel().state.first { !it.loading }.empty).isTrue()
    }

    private val drawn = TripsMapUiState(
        loading = false,
        heat = listOf(HeatPoint(12.9100, 77.6400), HeatPoint(12.9300, 77.6700), HeatPoint(12.9500, 77.7000)) +
            (0 until 20).map { HeatPoint(12.9500 + it * 0.0014, 77.7000 + it * 0.0025) },
        routes = 14,
        places = listOf(VisitedPlace(12.9100, 77.6400, 9), VisitedPlace(12.9780, 77.7500, 8)),
        fillUps = listOf(FillUpPin("f1", 12.9352, 77.6245, EnergyType.DIESEL)),
    )

    @Test fun mapLight() = capture("trips_map_light", false) { TripsMapContent(drawn, {}, {}) }

    @Test fun mapDark() = capture("trips_map_dark", true) { TripsMapContent(drawn, {}, {}) }

    @Test fun emptyLight() = capture("trips_map_empty_light", false) {
        TripsMapContent(TripsMapUiState(loading = false), {}, {})
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalMapsEnabled provides false) { FuelwiseTheme(darkTheme = dark) { content() } }
        }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

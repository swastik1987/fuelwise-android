package app.fuelwise.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.summary.ComputeVehicleSummary
import app.fuelwise.core.domain.trip.MonthTrips
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of design.md screen 5 and its brand-new state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class HomeScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 16, 10)), zone)
    private val summarize =
        ComputeVehicleSummary(ComputeConsumption(), ComputeCostMetrics(), ComputePredictions(clock), clock)
    private val actions = HomeActions({}, {}, { _, _ -> }, {})

    private fun ready(refuels: List<Refuel>, hide: Boolean = false): HomeUiState.Ready {
        val expenses = if (refuels.isEmpty()) emptyList() else listOf(SampleEntries.safariService)
        val cats = FakeCategoryRepository.SYSTEM
        val recent = (refuels.map(LogbookEntry::of) + expenses.map(LogbookEntry::of)).sortedByDescending {
            it.dateTimeUtcMs
        }
        return HomeUiState.Ready(
            vehicle = SampleVehicles.safari,
            vehicles = listOf(SampleVehicles.safari),
            summary = summarize(SampleVehicles.safari, refuels, expenses, cats, zone),
            recent = recent.take(3),
            categories = cats.associateBy { it.id },
            kmPerUnit = mapOf("r4" to 15.42, "r3" to 15.61),
            hideAmounts = hide,
            previousMonthLabel = "Sep",
            trips = if (refuels.isEmpty()) MonthTrips.NONE else MonthTrips(6, 214.0),
        )
    }

    @Test
    fun tripTilesOpenTripsAndStartATrip() {
        val taps = mutableListOf<String>()
        val withTrips = actions.copy(onOpenTrips = { taps += "trips" }, onStartTrip = { taps += "start" })
        compose.setContent { FuelwiseTheme { HomeContent(ready(SampleEntries.safariRefuels), withTrips, {}, {}) } }
        compose.onNodeWithText("Start trip").performScrollTo().performClick()
        compose.onNodeWithText("6").performScrollTo().performClick()
        assertThat(taps).containsExactly("start", "trips").inOrder()
    }

    @Test fun dashboardLight() = capture("home_light", false) {
        HomeContent(ready(SampleEntries.safariRefuels), actions, {}, {})
    }

    @Test fun autoTripsPausedLight() = capture("home_auto_paused_light", false) {
        HomeContent(
            ready(SampleEntries.safariRefuels),
            actions,
            {},
            {},
            autoTrips = AutoTripControls(AutoTripPill(pausedUntilUtcMs = Long.MAX_VALUE, oneTap = false)),
        )
    }

    @Test fun autoTripsOnDark() = capture("home_auto_on_dark", true) {
        HomeContent(
            ready(SampleEntries.safariRefuels),
            actions,
            {},
            {},
            autoTrips = AutoTripControls(AutoTripPill(pausedUntilUtcMs = null, oneTap = false)),
        )
    }

    @Test fun dashboardDark() = capture("home_dark", true) {
        HomeContent(ready(SampleEntries.safariRefuels), actions, {}, {})
    }

    @Test fun dashboardHidden() = capture("home_hidden_light", false) {
        HomeContent(ready(SampleEntries.safariRefuels, hide = true), actions, {}, {})
    }

    @Test fun brandNewLight() = capture("home_brand_new_light", false) {
        HomeContent(ready(emptyList()), actions, {}, {})
    }

    @Test fun brandNewDark() = capture("home_brand_new_dark", true) { HomeContent(ready(emptyList()), actions, {}, {}) }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

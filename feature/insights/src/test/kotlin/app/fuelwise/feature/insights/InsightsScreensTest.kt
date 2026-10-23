package app.fuelwise.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.insights.ComputeInsights
import app.fuelwise.core.domain.insights.ComputeRecap
import app.fuelwise.core.domain.insights.InsightPeriod
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of design.md screens 14, 15 and 16. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class InsightsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 16, 10)), zone)
    private val categories = FakeCategoryRepository.SYSTEM
    private val history =
        VehicleHistory(SampleVehicles.safari, SampleEntries.safariRefuels, listOf(SampleEntries.safariService))

    private fun snapshot(period: InsightPeriod) = InsightsSnapshot(
        vehicles = listOf(SampleVehicles.safari),
        selected = SampleVehicles.safari,
        allVehicles = false,
        period = period,
        categories = categories.associateBy { it.id },
        report = ComputeInsights(
            ComputeConsumption(),
            ComputeCostMetrics(),
            clock,
        )(listOf(history), categories, period, zone),
    )

    private val recap = RecapUiState(
        recap = ComputeRecap(
            ComputeConsumption(),
            ComputeCostMetrics(),
        )(history, categories, YearMonth.of(2026, 9), zone),
        vehicle = SampleVehicles.safari,
        showVehicleName = false,
    )

    @Test fun overviewLight() = capture("insights_light", false) {
        InsightsContent(snapshot(InsightPeriod.All), InsightsActions())
    }

    @Test fun overviewCustomLight() = capture("insights_custom_light", false) {
        val range = app.fuelwise.core.domain.insights.CustomRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30))
        InsightsContent(
            snapshot(InsightPeriod.Custom).copy(
                custom = range,
                report = ComputeInsights(ComputeConsumption(), ComputeCostMetrics(), clock)(
                    listOf(history),
                    categories,
                    InsightPeriod.Custom,
                    zone,
                    custom = range,
                ),
            ),
            InsightsActions(),
        )
    }

    // Tall enough to reach the cost of ownership card below the tiles.
    @Test
    @Config(qualifiers = "w412dp-h1600dp-xxhdpi")
    fun ownershipLight() = capture("insights_tco_light", false) {
        val bought = history.copy(
            vehicle = SampleVehicles.safari.copy(
                purchaseDate = java.time.LocalDate.of(2024, 3, 9),
                purchasePrice = app.fuelwise.core.model.Money(215_000_000),
            ),
        )
        val report = ComputeInsights(ComputeConsumption(), ComputeCostMetrics(), clock)(
            listOf(bought),
            categories,
            InsightPeriod.TwelveMonths,
            zone,
        )
        InsightsContent(snapshot(InsightPeriod.TwelveMonths).copy(report = report), InsightsActions())
    }

    @Test fun overviewDark() = capture("insights_dark", true) {
        InsightsContent(snapshot(InsightPeriod.All), InsightsActions())
    }

    @Test fun mileageDetailLight() = capture("chart_mileage_light", false) {
        ChartDetailContent(ChartKind.Mileage, snapshot(InsightPeriod.All), {})
    }

    @Test fun categoryDetailDark() = capture("chart_category_dark", true) {
        ChartDetailContent(ChartKind.SpendByCategory, snapshot(InsightPeriod.All), {})
    }

    @Test fun recapLight() = capture("recap_light", false) { RecapContent(recap, null, {}, {}, {}, {}) }

    @Test fun recapDark() = capture("recap_dark", true) { RecapContent(recap, null, {}, {}, {}, {}) }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        // Vico builds its chart model off the main thread; let it land before capturing.
        repeat(CHART_SETTLE_ROUNDS) {
            Thread.sleep(CHART_SETTLE_MS)
            compose.waitForIdle()
        }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val CHART_SETTLE_ROUNDS = 5
        const val CHART_SETTLE_MS = 100L
    }
}

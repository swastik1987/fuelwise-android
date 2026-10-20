package app.fuelwise.feature.dataimport

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.domain.report.BuildReports
import app.fuelwise.core.domain.report.ReportPeriod
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of Export & reports (PRD F-13.1, F-13.3): ready, business report, empty, error. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ExportScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val reports = BuildReports(ComputeCostMetrics(), ComputeConsumption())
    private val vehicles = listOf(SampleVehicles.safari, SampleVehicles.ertiga)
    private val month = YearMonth.of(2026, 10)
    private val categories = FakeCategoryRepository.SYSTEM

    private val ready = ExportUiState(
        loading = false,
        vehicles = vehicles,
        categories = categories,
        reportVehicleId = "safari",
        month = month,
        thisMonth = month,
        report = Report.Vehicle(
            reports.vehicle(
                VehicleHistory(SampleVehicles.safari, SampleEntries.safariRefuels, listOf(SampleEntries.safariService)),
                categories,
                ReportPeriod.month(month),
                zone,
            ),
        ),
    )

    private val business = ready.copy(
        reportKind = ReportKind.Business,
        reportVehicleId = null,
        rateText = "10.50",
        report = Report.Business(
            reports.businessMileage(
                listOf(
                    Trip(
                        "t1",
                        "safari",
                        LocalDateTime.of(2026, 10, 6, 9, 0).atZone(zone).toInstant().toEpochMilli(),
                        zone.id,
                        42.5,
                        TripPurpose.Business,
                        fromPlace = "HSR Layout",
                        toPlace = "Whitefield",
                    ),
                ),
                null,
                ReportPeriod.month(month),
                Money(1_050),
                zone,
            ),
        ),
    )

    @Test fun readyLight() = capture("export_light", false) { ExportContent(ready, ExportActions()) }

    @Test fun readyDark() = capture("export_dark", true) { ExportContent(ready, ExportActions()) }

    @Test fun businessLight() = capture("export_business_light", false) { ExportContent(business, ExportActions()) }

    @Test fun businessDark() = capture("export_business_dark", true) { ExportContent(business, ExportActions()) }

    @Test fun emptyLight() = capture("export_empty_light", false) {
        ExportContent(ExportUiState(loading = false), ExportActions())
    }

    @Test fun emptyDark() = capture("export_empty_dark", true) {
        ExportContent(ExportUiState(loading = false), ExportActions())
    }

    @Test fun writeFailedLight() = capture("export_error_light", false) {
        ExportContent(ready.copy(problem = ExportProblem.WriteFailed), ExportActions())
    }

    @Test fun nothingToExportDark() = capture("export_nothing_dark", true) {
        ExportContent(ready.copy(problem = ExportProblem.NothingToExport), ExportActions())
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

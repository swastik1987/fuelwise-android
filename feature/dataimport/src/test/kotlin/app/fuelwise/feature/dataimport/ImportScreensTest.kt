package app.fuelwise.feature.dataimport

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.importing.ColumnMapping
import app.fuelwise.core.domain.importing.DateOrder
import app.fuelwise.core.domain.importing.EntryKind
import app.fuelwise.core.domain.importing.ImportField
import app.fuelwise.core.domain.importing.ImportPlan
import app.fuelwise.core.domain.importing.RowIssue
import app.fuelwise.core.domain.importing.RowProblem
import app.fuelwise.core.domain.importing.RowRef
import app.fuelwise.core.model.ImportBatch
import app.fuelwise.core.model.ImportSource
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of the import flow (PRD F-13.2): start, column mapper, preview. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ImportScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val vehicles = listOf(SampleVehicles.safari, SampleVehicles.ertiga)

    private val start = ImportUiState(
        vehicles = vehicles,
        batches = listOf(ImportBatch("b1", ImportSource.Fuelio, "Fuelio-Safari.csv", 1_760_589_000_000, 112, 18)),
    )

    private val mapper = ImportUiState(
        step = ImportStep.Mapping,
        vehicles = vehicles,
        mapper = MapperState(
            headers = listOf("Day", "Odo", "Litres", "Rate", "Paid by", "Remarks"),
            samples = listOf("16/10/2026", "42318", "40.25", "88.95", "UPI", "Highway"),
            mapping = ColumnMapping(
                EntryKind.Fuel,
                mapOf(
                    ImportField.Odometer to 1,
                    ImportField.Quantity to 2,
                    ImportField.UnitPrice to 3,
                    ImportField.Notes to 5,
                ),
            ),
            dateOrder = DateOrder.DayMonthYear,
        ),
    )

    private val preview = ImportUiState(
        step = ImportStep.Preview,
        vehicles = vehicles,
        preview = PreviewState(
            source = ImportSource.Template,
            fileName = "fuelwise-template.xlsx",
            plan = ImportPlan(
                refuels = SampleEntries.safariRefuels,
                expenses = listOf(SampleEntries.safariService),
                duplicates = 2,
                problems = listOf(
                    RowProblem(RowRef("Fuel", 14), RowIssue.VehicleNotFound, "Swift"),
                    RowProblem(RowRef("Expenses", 6), RowIssue.BadDate, "31/02/2026"),
                ),
                otherCategoryRows = 1,
            ),
            askVehicle = true,
            vehicleId = "safari",
        ),
    )

    @Test fun startLight() = capture("import_light", false) { ImportContent(start, ImportActions(), zone) }

    @Test fun startDark() = capture("import_dark", true) { ImportContent(start, ImportActions(), zone) }

    @Test fun startNoVehicle() = capture("import_error_light", false) {
        ImportContent(ImportUiState(error = ImportError.NoVehicles), ImportActions(), zone)
    }

    @Test fun mapperLight() = capture("import_mapper_light", false) { ImportContent(mapper, ImportActions(), zone) }

    @Test fun mapperDark() = capture("import_mapper_dark", true) { ImportContent(mapper, ImportActions(), zone) }

    @Test fun previewLight() = capture("import_preview_light", false) { ImportContent(preview, ImportActions(), zone) }

    @Test fun previewDark() = capture("import_preview_dark", true) { ImportContent(preview, ImportActions(), zone) }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

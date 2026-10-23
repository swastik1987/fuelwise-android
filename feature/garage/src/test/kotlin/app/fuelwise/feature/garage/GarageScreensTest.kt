package app.fuelwise.feature.garage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.vehicle.VehicleDraftError
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.feature.garage.detail.VehicleDetailContent
import app.fuelwise.feature.garage.editor.VehicleDetail
import app.fuelwise.feature.garage.editor.VehicleEditorActions
import app.fuelwise.feature.garage.editor.VehicleEditorContent
import app.fuelwise.feature.garage.editor.VehicleFormState
import app.fuelwise.feature.garage.list.GarageContent
import app.fuelwise.feature.garage.list.GarageUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI behaviour plus light/dark screenshots of the Garage screens. Record goldens with
 * `./gradlew :feature:garage:recordRoborazziDebug` (on CI, so fonts match).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class GarageScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addVehicleFormShowsErrorsOnlyAfterSave() {
        var state by mutableStateOf(VehicleFormState(onboarding = true))
        compose.setContent {
            FuelwiseTheme {
                VehicleEditorContent(
                    state = state,
                    onBack = {},
                    actions = VehicleEditorActions(
                        onSave = {
                            state =
                                state.copy(
                                    errors = setOf(VehicleDraftError.NameRequired, VehicleDraftError.OdometerRequired),
                                )
                        },
                    ),
                )
            }
        }
        compose.onNodeWithText("Give it a name, like Safari or Activa.").assertDoesNotExist()
        compose.onNodeWithText("Add vehicle").performClick()
        compose.onNodeWithText("Give it a name, like Safari or Activa.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enter the reading on your odometer.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun garageLight() = capture("garage_light", dark = false) { GarageList() }

    @Test
    fun garageDark() = capture("garage_dark", dark = true) { GarageList() }

    @Test
    fun editorLight() = capture("editor_bifuel_light", dark = false) { BiFuelEditor() }

    @Test
    fun editorDark() = capture("editor_bifuel_dark", dark = true) { BiFuelEditor() }

    // Tall enough to show "More details" below the fold.
    @Test
    @Config(qualifiers = TALL)
    fun detailsEditorLight() = capture("editor_details_light", dark = false) { HybridEditor() }

    @Test
    @Config(qualifiers = TALL)
    fun detailsEditorDark() = capture("editor_details_dark", dark = true) { HybridEditor() }

    @Test
    fun detailPhotoLight() = capture("detail_photo_light", dark = false) {
        VehicleDetailContent(SampleVehicles.safari, {}, {}, {}, {}, { false }, photo = samplePhoto())
    }

    @Test
    fun detailLight() = capture("detail_light", dark = false) { Detail() }

    @Test
    fun detailDark() = capture("detail_dark", dark = true) { Detail() }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun GarageList() = GarageContent(
        GarageUiState.Ready(
            SampleVehicles.all.filterNot {
                it.isArchived
            },
            SampleVehicles.all.filter { it.isArchived },
        ),
        onOpenVehicle = {},
        onAddVehicle = {},
    )

    @Composable
    private fun BiFuelEditor() = VehicleEditorContent(
        state = VehicleFormState(
            onboarding = true,
            name = "Ertiga",
            fuelSetup = app.fuelwise.core.model.FuelSetup.CngPetrol,
            odometer = "31,045",
            capacities = listOf("12", "45"),
            registration = "MH 02 CD 5678",
            moreExpanded = true,
        ),
        onBack = {},
        actions = VehicleEditorActions(),
    )

    @Composable
    private fun Detail() = VehicleDetailContent(
        SampleVehicles.safari.copy(
            make = "Tata",
            model = "Safari",
            variant = "XZ+",
            year = 2023,
            purchaseDate = java.time.LocalDate.of(2023, 3, 9),
            purchaseOdometerKm = 12.0,
            purchasePrice = app.fuelwise.core.model.Money(215_000_000),
            custom = app.fuelwise.core.model.CustomParameters(kmPerUnit = 14.5, rangeKm = 700.0),
        ),
        {},
        {},
        {},
        {},
        { false },
    )

    @Composable
    private fun HybridEditor() = VehicleEditorContent(
        state = VehicleFormState(
            isEditing = true,
            name = "City",
            odometer = "8,200",
            details = mapOf(
                VehicleDetail.Make to "Honda",
                VehicleDetail.Model to "City",
                VehicleDetail.Variant to "e:HEV ZX",
                VehicleDetail.Year to "2025",
                VehicleDetail.PurchasePrice to "20,50,000",
            ),
            purchaseDate = java.time.LocalDate.of(2025, 2, 14),
            isHybrid = true,
            defaultGrade = app.fuelwise.core.model.FuelGrade.E20,
            moreExpanded = true,
        ),
        onBack = {},
        actions = VehicleEditorActions(),
    )

    /** A stand-in photo: sky over road, drawn so the screenshot doesn't depend on a file. */
    private fun samplePhoto(): androidx.compose.ui.graphics.ImageBitmap {
        val bitmap = androidx.core.graphics.createBitmap(1200, 700)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(142, 178, 204))
            drawRect(
                0f,
                420f,
                1200f,
                700f,
                android.graphics.Paint().apply {
                    color =
                        android.graphics.Color.rgb(70, 72, 74)
                },
            )
        }
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val TALL = "w412dp-h2400dp-xxhdpi"
    }
}

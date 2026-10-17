package app.fuelwise.feature.garage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private fun Detail() = VehicleDetailContent(SampleVehicles.safari, {}, {}, {}, {}, { false })
}

package app.fuelwise.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.PrivatePlace
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.domain.units.EfficiencyUnit
import app.fuelwise.core.model.Money
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.feature.settings.privacy.PrivatePlacesActions
import app.fuelwise.feature.settings.privacy.PrivatePlacesContent
import app.fuelwise.feature.settings.privacy.PrivatePlacesUiState
import app.fuelwise.feature.settings.units.UnitsActions
import app.fuelwise.feature.settings.units.UnitsContent
import app.fuelwise.feature.settings.units.UnitsUiState
import app.fuelwise.feature.settings.units.VehicleUnits
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings behaviour plus light/dark screenshots. Record goldens with
 * `./gradlew :feature:settings:recordRoborazziDebug` (on CI, so fonts match).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class SettingsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = SettingsUiState(
        loading = false,
        homeTariff = Money(820),
        upiVpa = "asha.k@okaxis",
        appLock = true,
        appLockTimeoutMinutes = 1,
        monthlyRecap = true,
        vehicleUnitOverrides = 1,
        driveConnected = true,
    )
    private val chrome = SettingsChrome(versionName = "0.1.0", dynamicColorSupported = true)

    @Test
    fun deleteNeedsTheTypedWordAndOffersDrive() {
        var confirmed: Boolean? = null
        compose.setContent {
            FuelwiseTheme {
                DeleteAllForm(driveConnected = true, deleting = false, onConfirm = { confirmed = it }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Delete everything").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("delete")
        compose.onNodeWithText("Also delete my Google Drive backups").performClick()
        compose.onNodeWithText("Delete everything").assertIsEnabled().performClick()
        assertThat(confirmed).isTrue()
    }

    @Test
    fun rowsShowCurrentValues() {
        compose.setContent { FuelwiseTheme { SettingsContent(state, chrome, SettingsActions()) } }
        compose.onNodeWithText("₹8.20 per kWh").assertExists()
        compose.onNodeWithText("asha.k@okaxis").assertExists()
        compose.onNodeWithText("km/L · 1 vehicle has its own").assertExists()
        // Rows owned by sessions not merged yet stay hidden until the app shell links them.
        compose.onNodeWithText("Home city").assertDoesNotExist()
        compose.onNodeWithText("Delete all data").performScrollTo()
    }

    @Test
    fun settingsLight() = capture("settings_light", dark = false) { Settings() }

    @Test
    fun settingsDark() = capture("settings_dark", dark = true) { Settings() }

    @Test
    fun deleteDialogLight() = capture("settings_delete_light", dark = false) {
        DeleteForm()
    }

    @Test
    fun deleteDialogDark() = capture("settings_delete_dark", dark = true) {
        DeleteForm()
    }

    @Test
    fun unitsLight() = capture("units_light", dark = false) { Units() }

    @Test
    fun unitsDark() = capture("units_dark", dark = true) { Units() }

    @Test
    fun settingsLargeFont() {
        compose.setContent {
            FuelwiseTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = LocalDensity.current.density,
                        fontScale = 2f,
                    ),
                ) { Settings() }
            }
        }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/settings_font200.png")
    }

    // The form alone: Robolectric never idles with a dialog window, so tests render the dialog's content directly.
    @Composable
    private fun DeleteForm() = Box(Modifier.background(FwTheme.colors.bg).padding(FwTheme.spacing.gutter)) {
        Surface(shape = FwTheme.radius.card, color = FwTheme.colors.surface) {
            DeleteAllForm(driveConnected = true, deleting = false, onConfirm = {}, onDismiss = {})
        }
    }

    @Test
    fun confirmationWordIgnoresCaseAndSpaces() {
        assertThat(deleteConfirmed(" delete ", "DELETE")).isTrue()
        assertThat(deleteConfirmed("DELET", "DELETE")).isFalse()
    }

    @Composable
    private fun Settings() = SettingsContent(state, chrome, SettingsActions())

    @Composable
    private fun Units() = UnitsContent(
        UnitsUiState(
            loading = false,
            default = DisplayUnits(efficiency = EfficiencyUnit.DISTANCE_PER_QUANTITY),
            vehicles = listOf(
                VehicleUnits(SampleVehicles.safari, null),
                VehicleUnits(
                    SampleVehicles.nexonEv,
                    DisplayUnits(efficiency = EfficiencyUnit.QUANTITY_PER_100_DISTANCE),
                ),
            ),
        ),
        UnitsActions(),
    )

    @Test fun privatePlacesLight() = capture("private_places_light", false) {
        PrivatePlacesContent(
            PrivatePlacesUiState(
                loading = false,
                places = listOf(PrivatePlace("p1", "Home", 12.91, 77.64), PrivatePlace("p2", "Office", 12.97, 77.75)),
            ),
            PrivatePlacesActions(),
        )
    }

    @Test fun privatePlacesEmptyDark() = capture("private_places_empty_dark", true) {
        PrivatePlacesContent(PrivatePlacesUiState(loading = false), PrivatePlacesActions())
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

package app.fuelwise.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.domain.units.EfficiencyUnit
import app.fuelwise.core.model.Money
import app.fuelwise.core.testing.SampleVehicles
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
                SettingsContent(
                    state,
                    chrome,
                    SettingsActions(onDeleteAll = { confirmed = it }),
                    initialSheet = "Delete",
                )
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
        compose.onNodeWithText("km · L · km/L · 1 vehicle has its own").assertExists()
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
        SettingsContent(state, chrome, SettingsActions(), initialSheet = "Delete")
    }

    @Test
    fun deleteDialogDark() = capture("settings_delete_dark", dark = true) {
        SettingsContent(state, chrome, SettingsActions(), initialSheet = "Delete")
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

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        val root = if (name.contains("delete")) compose.onNode(isDialog()) else compose.onNode(isRoot())
        root.captureRoboImage("src/test/screenshots/$name.png")
    }
}

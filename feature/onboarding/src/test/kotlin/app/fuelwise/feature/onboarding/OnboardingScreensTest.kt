package app.fuelwise.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.model.Cities
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Behaviour plus light/dark screenshots of design.md screens 1, 3 and 4. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class OnboardingScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun privacyTogglesReflectState() {
        compose.setContent {
            FuelwiseTheme { PrivacyContent(PrivacyUiState(weeklyDigest = false, monthlyRecap = true), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Weekly digest", substring = true).assertIsOff()
        compose.onNodeWithText("Monthly recap", substring = true).assertIsOn()
    }

    @Test
    fun noCrashReportToggleInR1() {
        compose.setContent { FuelwiseTheme { PrivacyContent(PrivacyUiState(), {}, {}, {}, {}) } }
        compose.onNodeWithText("crash", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun welcomeLight() = capture("welcome_light", dark = false) { WelcomeScreen(onGetStarted = {}) }

    @Test
    fun welcomeDark() = capture("welcome_dark", dark = true) { WelcomeScreen(onGetStarted = {}) }

    @Test
    fun historyLight() = capture("history_light", dark = false) { History() }

    @Test
    fun historyDark() = capture("history_dark", dark = true) { History() }

    @Test
    fun privacyLight() = capture("privacy_light", dark = false) { PrivacyContent(PrivacyUiState(), {}, {}, {}, {}) }

    @Test
    fun privacyDark() = capture("privacy_dark", dark = true) { PrivacyContent(PrivacyUiState(), {}, {}, {}, {}) }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun History() = HistoryContent(HistoryUiState(selected = Cities.byId("bengaluru")), {}, {}, {}, {})
}

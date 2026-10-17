package app.fuelwise.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.model.BackupError
import app.fuelwise.core.model.BackupFrequency
import app.fuelwise.core.model.BackupSettings
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of design.md screen 27: connected, error, and not set up. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class BackupScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = 1_760_589_000_000L
    private val connected = BackupUiState(
        settings = BackupSettings(
            frequency = BackupFrequency.Daily,
            encrypted = true,
            accountEmail = "user@example.com",
            lastSuccessUtcMs = now - 2 * 60 * 60 * 1000,
            lastSizeBytes = 184_320,
        ),
        nowUtcMs = now,
    )

    @Test fun connectedLight() = capture("backup_light", false) { BackupContent(connected, BackupActions()) }

    @Test fun connectedDark() = capture("backup_dark", true) { BackupContent(connected, BackupActions()) }

    @Test fun errorLight() = capture("backup_error_light", false) {
        BackupContent(
            connected.copy(settings = connected.settings.copy(lastError = BackupError.SignIn)),
            BackupActions(),
        )
    }

    @Test fun notConfiguredLight() = capture("backup_not_configured_light", false) {
        BackupContent(BackupUiState(configured = false, nowUtcMs = now), BackupActions())
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

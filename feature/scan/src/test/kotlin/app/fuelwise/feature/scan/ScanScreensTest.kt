package app.fuelwise.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.scan.OcrBox
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.ScanPrefill
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of design.md screen 9: live labels, confirm sheets, failure and camera-denied states. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScanScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val read = ScanPrefill(total = Money(200_000), quantity = 19.43, unitPrice = Money(10_292))

    private val live = ScanUiState(
        camera = CameraAccess.Granted,
        live = read,
        labels = listOf(
            LiveLabel(ScanField.Total, OcrBox(220, 420, 520, 480)),
            LiveLabel(ScanField.Quantity, OcrBox(240, 540, 500, 600)),
            LiveLabel(ScanField.Rate, OcrBox(240, 660, 500, 720)),
        ),
        frameWidth = 720,
        frameHeight = 1280,
        allValid = true,
    )

    private val checkSheet = live.copy(
        allValid = false,
        review = ScanReview(
            values = read.copy(quantity = 19.48),
            fields = listOf(ScanField.Total, ScanField.Quantity, ScanField.Rate),
            confirmed = setOf(ScanField.Total),
            validated = false,
            hasPhoto = true,
        ),
    )

    private val odometer = ScanUiState(
        mode = ScanMode.Odometer,
        camera = CameraAccess.Granted,
        live = ScanPrefill(odometerKm = 42_318),
        review = ScanReview(
            values = ScanPrefill(odometerKm = 42_318),
            fields = listOf(ScanField.Odometer),
            confirmed = setOf(ScanField.Odometer),
            validated = true,
            hasPhoto = false,
        ),
    )

    private val struggling = ScanUiState(camera = CameraAccess.Granted, struggling = true)
    private val denied = ScanUiState(camera = CameraAccess.Denied)

    @Test fun liveLight() = capture("scan_live_light", false) { ScanContent(live, ScanActions(), fakeCamera) }

    @Test fun liveDark() = capture("scan_live_dark", true) { ScanContent(live, ScanActions(), fakeCamera) }

    @Test fun confirmLight() = capture("scan_confirm_light", false) {
        ScanContent(checkSheet, ScanActions(), fakeCamera)
    }

    @Test fun confirmDark() = capture("scan_confirm_dark", true) { ScanContent(checkSheet, ScanActions(), fakeCamera) }

    @Test fun strugglingLight() = capture("scan_struggling_light", false) {
        ScanContent(struggling, ScanActions(), fakeCamera)
    }

    @Test fun strugglingDark() = capture("scan_struggling_dark", true) {
        ScanContent(struggling, ScanActions(), fakeCamera)
    }

    @Test fun deniedLight() = capture("scan_denied_light", false) { ScanContent(denied, ScanActions(), fakeCamera) }

    @Test fun deniedDark() = capture("scan_denied_dark", true) { ScanContent(denied, ScanActions(), fakeCamera) }

    @Test fun odometerLight() = capture("scan_odometer_light", false) {
        ScanContent(odometer, ScanActions(), fakeCamera)
    }

    @Test fun odometerDark() = capture("scan_odometer_dark", true) {
        ScanContent(odometer, ScanActions(), fakeCamera)
    }

    /** Stands in for the camera feed, which Robolectric can't render. */
    private val fakeCamera: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(FwTheme.colors.ink3))
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

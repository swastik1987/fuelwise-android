package app.fuelwise.widget

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderKind
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Light/dark screenshots of design.md screen 30 at home-screen sizes: Glance content is composed to RemoteViews,
 * applied to a real view and captured, as a launcher would draw it.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class WidgetScreenshotTest {
    private val status = StatusWidgetModel(
        vehicleName = "Safari",
        costPerKm = Money(482),
        hideAmounts = false,
        lastKmPerUnit = 16.8,
        unit = QuantityUnit.LITRE,
        isEstimate = false,
        nextDue = ReminderStatus(
            reminder = Reminder("puc", "safari", ReminderKind.Puc, null, null, null),
            daysLeft = 12,
            kmLeft = null,
            predictedOdometerDate = null,
            urgency = Urgency.Soon,
            notifyToday = false,
        ),
    )

    @Test fun quickAddLight() = capture("widget_quick_add_light", false, ONE_ROW) { QuickAddContent("nexon") }

    @Test fun quickAddDark() = capture("widget_quick_add_dark", true, ONE_ROW) { QuickAddContent(null) }

    @Test fun statusLight() = capture("widget_status_light", false, TWO_ROWS) { StatusContent(status) }

    @Test fun statusDark() = capture("widget_status_dark", true, TWO_ROWS) { StatusContent(status) }

    @Test fun statusHiddenAmounts() =
        capture("widget_status_hidden_light", false, TWO_ROWS) { StatusContent(status.copy(hideAmounts = true)) }

    @Test fun statusNoVehicle() = capture("widget_status_empty_dark", true, TWO_ROWS) { StatusContent(null) }

    @Test fun tripIdleLight() =
        capture("widget_trip_idle_light", false, ONE_ROW) { TripContent(TripWidgetModel.Idle(startInPlace = true)) }

    @Test fun tripRecordingDark() =
        capture("widget_trip_recording_dark", true, ONE_ROW) { TripContent(TripWidgetModel.Recording(23.4, false)) }

    @Test fun tripPausedLight() =
        capture("widget_trip_paused_light", false, ONE_ROW) { TripContent(TripWidgetModel.Recording(12.0, true)) }

    private fun capture(name: String, night: Boolean, size: DpSize, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = runBlocking { GlanceRemoteViews().compose(context, size) { content() } }
        val host = FrameLayout(context)
        val view = result.remoteViews.apply(context, host)
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).roundToInt()
        val height = (size.height.value * density).roundToInt()
        host.addView(view, FrameLayout.LayoutParams(width, height))
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        // Not attached to an Activity, so draw it to a bitmap ourselves, as the launcher would.
        val bitmap = createBitmap(width, height)
        host.draw(Canvas(bitmap))
        bitmap.captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        // A Pixel-class launcher cell is about 90 × 100 dp.
        val ONE_ROW = DpSize(180.dp, 80.dp)
        val TWO_ROWS = DpSize(180.dp, 180.dp)
    }
}

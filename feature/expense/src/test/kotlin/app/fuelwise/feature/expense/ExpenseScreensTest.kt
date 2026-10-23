package app.fuelwise.feature.expense

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The toll sheet with trips to link it to (PRD F-10.2). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ExpenseScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-16T04:30:00Z").toEpochMilli()
    private val trips = listOf(
        Trip(
            "mysuru",
            "safari",
            now - HOUR * 3,
            "Asia/Kolkata",
            146.0,
            TripPurpose.Personal,
            endUtcMs = now,
            fromPlace = "HSR Layout",
            toPlace = "Mysuru",
        ),
        Trip(
            "office",
            "safari",
            now - HOUR * 26,
            "Asia/Kolkata",
            18.0,
            TripPurpose.Commute,
            endUtcMs = now - HOUR * 25,
        ),
    )
    private val state = ExpenseFormState(
        loading = false,
        isToll = true,
        vehicle = SampleVehicles.safari,
        vehicles = listOf(SampleVehicles.safari),
        amount = "165",
        dateTimeUtcMs = now,
        plaza = "Kaniminike",
        focus = null,
        tripId = "mysuru",
        trips = trips,
    )

    @Test fun tollWithTripLight() = capture("expense_toll_trip_light", false)

    @Test fun tollWithTripDark() = capture("expense_toll_trip_dark", true)

    private fun capture(name: String, dark: Boolean) {
        compose.setContent {
            FuelwiseTheme(darkTheme = dark) { ExpenseContent(state, ExpenseActions(), onClose = {}, nowUtcMs = now) }
        }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
    }
}

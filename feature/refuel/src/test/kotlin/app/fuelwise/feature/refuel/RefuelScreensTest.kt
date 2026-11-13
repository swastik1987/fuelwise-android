package app.fuelwise.feature.refuel

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.entry.FillReward
import app.fuelwise.core.domain.entry.PriceTriangle
import app.fuelwise.core.domain.station.StationOption
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceSource
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of design.md screens 6, 7 and 8. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class RefuelScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = 1_760_589_000_000L

    private val fuel = RefuelFormState(
        loading = false,
        vehicle = SampleVehicles.safari,
        dateTimeUtcMs = now,
        predictedOdometer = 42_318.0,
        quantity = "32.40",
        unitPrice = "102.92",
        total = "3334.61",
        recentEdits = listOf(PriceTriangle.Field.Quantity, PriceTriangle.Field.UnitPrice),
        priceSource = PriceSource.LastPaid,
        focus = NumericField.Quantity,
    )

    private val charge = RefuelFormState(
        loading = false,
        vehicle = SampleVehicles.nexonEv,
        dateTimeUtcMs = now,
        predictedOdometer = 18_402.0,
        quantity = "24.6",
        unitPrice = "7.50",
        total = "184.50",
        recentEdits = listOf(PriceTriangle.Field.Quantity, PriceTriangle.Field.UnitPrice),
        socStart = 22f,
        socEnd = 84f,
        charger = ChargerType.HomeAc,
        focus = null,
    )

    @Test fun fuelLight() = capture("add_fuel_light", false) { RefuelContent(fuel, RefuelActions(), {}, now) }

    @Test fun fuelDark() = capture("add_fuel_dark", true) { RefuelContent(fuel, RefuelActions(), {}, now) }

    @Test fun tripMeterLight() = capture("add_fuel_trip_meter_light", false) {
        val trip = fuel.copy(tripMeter = "418.5", previousFillOdometer = 41_900.0, odometer = "42318.5")
        RefuelContent(trip, RefuelActions(), {}, now)
    }

    @Test fun tripMeterDark() = capture("add_fuel_trip_meter_dark", true) {
        val trip = fuel.copy(tripMeter = "418.5", previousFillOdometer = 41_900.0, odometer = "42318.5")
        RefuelContent(trip, RefuelActions(), {}, now)
    }

    @Test fun chargeLight() = capture("add_charge_light", false) { RefuelContent(charge, RefuelActions(), {}, now) }

    @Test fun rewardLight() = capture("reward_light", false) {
        RefuelContent(
            fuel.copy(
                focus = null,
                reward = FillReward.Measured(
                    Interval(412.0, 24.52),
                    changeVsAverage = 0.04,
                    costPerKm = Money(592),
                    unusual = false,
                ),
            ),
            RefuelActions(),
            {},
            now,
        )
    }

    // Station auto-detect (PRD F-21.1): keypad closed so the station row shows.
    private val detected = fuel.copy(
        focus = null,
        station = "HP · Sri Lakshmi Fuels",
        stationDetect = StationDetect.Found(
            listOf(
                StationOption("HP · Sri Lakshmi Fuels", 12.9787, 77.6408, 38.0, usedBefore = false),
                StationOption("IndianOil · 100 Ft Rd", 12.9778, 77.6408, 71.0, usedBefore = false),
                StationOption("BPCL", 12.9795, 77.6408, 122.0, usedBefore = false),
            ),
            selected = 0,
        ),
    )

    @Test fun stationDetectedLight() = capture("add_fuel_station_light", false) {
        RefuelContent(detected, RefuelActions(), {}, now)
    }

    @Test fun stationDetectedDark() = capture("add_fuel_station_dark", true) {
        RefuelContent(detected, RefuelActions(), {}, now)
    }

    @Test fun stationOtherLight() = capture("add_fuel_station_other_light", false) {
        val other = detected.copy(
            station = "Raju Fuels",
            stationDetect = (detected.stationDetect as StationDetect.Found).copy(selected = null),
        )
        RefuelContent(other, RefuelActions(), {}, now)
    }

    @Test fun stationNeedsPermissionLight() = capture("add_fuel_station_permission_light", false) {
        RefuelContent(fuel.copy(focus = null, stationDetect = StationDetect.NeedsPermission), RefuelActions(), {}, now)
    }

    @Test fun stationNotFoundDark() = capture("add_fuel_station_none_dark", true) {
        RefuelContent(fuel.copy(focus = null, stationDetect = StationDetect.NotFound), RefuelActions(), {}, now)
    }

    @Test
    fun tappingAStationChipSelectsIt() {
        var picked: Int? = -1
        compose.setContent {
            FuelwiseTheme { RefuelContent(detected, RefuelActions(onStationOption = { picked = it }), {}, now) }
        }
        compose.onNodeWithText("IndianOil · 100 Ft Rd").performScrollTo().performClick()
        assertThat(picked).isEqualTo(1)
        compose.onNodeWithText("Other…").performScrollTo().performClick()
        assertThat(picked).isNull()
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

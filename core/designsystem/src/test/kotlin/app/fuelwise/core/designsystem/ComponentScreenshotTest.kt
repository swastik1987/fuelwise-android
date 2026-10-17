package app.fuelwise.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.component.Delta
import app.fuelwise.core.designsystem.component.FwBottomBar
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwEntryRow
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwHeroMetric
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwMetricTile
import app.fuelwise.core.designsystem.component.FwNavItem
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.EnergyColors
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.designsystem.theme.FwTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Component sheet in light and dark. Golden images: `./gradlew :core:designsystem:recordRoborazziDebug`,
 * then compare with `verifyRoborazziDebug`. Record goldens on CI (Linux) so font rendering matches.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ComponentScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun componentsLight() = capture(dark = false, name = "components_light")

    @Test
    fun componentsDark() = capture(dark = true, name = "components_dark")

    private fun capture(dark: Boolean, name: String) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { ComponentSheet() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

@Composable
private fun ComponentSheet() {
    Column(
        Modifier.background(FwTheme.colors.bg).width(412.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FwHeroMetric(
            overline = "This month · cost per km",
            value = "₹4.82",
            unit = "/km",
            delta = Delta("6% vs Sep", Delta.Direction.Down, isImprovement = true),
            onInfoClick = {},
            infoContentDescription = "How this is calculated",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FwMetricTile("Mileage", "16.8", Modifier.weight(1f), unit = "km/L", caption = "avg 16.1 · 30 days")
            FwMetricTile("Spent", "₹6,240", Modifier.weight(1f), caption = "1,294 km this month")
        }
        FwEntryRow(
            FwIcons.Fuel,
            EnergyColors.Petrol,
            "Petrol · 32.4 L",
            "Shell, HSR Layout · 42,318 km",
            "₹3,334",
            chip = "16.8 km/L",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FwFilterChip("All", selected = true, onClick = {})
            FwFilterChip("Fuel", selected = false, onClick = {})
            FwChoiceChip("Regular", selected = true, onClick = {})
            FwChoiceChip("E20", selected = false, onClick = {})
        }
        FwPrimaryButton("Save", onClick = {})
        FwInkButton("Continue", onClick = {})
        FwSecondaryButton("Restore", onClick = {})
        FwBottomBar(
            items = listOf(
                FwNavItem("Home", FwIcons.Home, FwIcons.HomeFilled),
                FwNavItem("Logbook", FwIcons.Logbook, FwIcons.LogbookFilled),
                FwNavItem("Insights", FwIcons.Insights, FwIcons.InsightsFilled),
                FwNavItem("Garage", FwIcons.Garage, FwIcons.GarageFilled),
            ),
            selectedIndex = 0,
            onSelect = {},
            onFabClick = {},
            fabContentDescription = "Add",
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}

package app.fuelwise.navigation

import androidx.annotation.StringRes
import app.fuelwise.R
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.feature.garage.GarageRoute
import app.fuelwise.feature.home.HomeRoute
import app.fuelwise.feature.insights.InsightsRoute
import app.fuelwise.feature.logbook.LogbookRoute
import kotlin.reflect.KClass

/** The four bottom-nav tabs around the "+" FAB (docs/design.md §6). */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val icon: Int,
    val selectedIcon: Int,
) {
    Home(HomeRoute, HomeRoute::class, R.string.nav_home, FwIcons.Home, FwIcons.HomeFilled),
    Logbook(LogbookRoute, LogbookRoute::class, R.string.nav_logbook, FwIcons.Logbook, FwIcons.LogbookFilled),
    Insights(InsightsRoute, InsightsRoute::class, R.string.nav_insights, FwIcons.Insights, FwIcons.InsightsFilled),
    Garage(GarageRoute, GarageRoute::class, R.string.nav_garage, FwIcons.Garage, FwIcons.GarageFilled),
}

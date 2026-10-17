package app.fuelwise.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fuelwise.R
import app.fuelwise.core.designsystem.component.FwBottomBar
import app.fuelwise.core.designsystem.component.FwNavItem
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.feature.garage.garageScreen
import app.fuelwise.feature.home.HomeRoute
import app.fuelwise.feature.home.homeScreen
import app.fuelwise.feature.insights.insightsScreen
import app.fuelwise.feature.logbook.logbookScreen

/** App shell: bottom nav + FAB around a single NavHost. */
@Composable
fun FuelwiseApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val destinations = TopLevelDestination.entries
    val selectedIndex = destinations.indexOfFirst { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
    }.coerceAtLeast(0)

    // The add-entry sheet (F-02) arrives in M3; until then "+" and the empty-state actions have nothing to open.
    val openAddEntry: () -> Unit = {}

    Scaffold(
        containerColor = FwTheme.colors.bg,
        bottomBar = {
            FwBottomBar(
                items = destinations.map { FwNavItem(stringResource(it.label), it.icon, it.selectedIcon) },
                selectedIndex = selectedIndex,
                onSelect = { index -> navController.navigateToTopLevel(destinations[index]) },
                onFabClick = openAddEntry,
                fabContentDescription = stringResource(R.string.nav_add_entry),
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            homeScreen(onLogFuel = openAddEntry)
            logbookScreen(onPrimaryAction = openAddEntry)
            insightsScreen(onPrimaryAction = openAddEntry)
            garageScreen(onPrimaryAction = {})
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

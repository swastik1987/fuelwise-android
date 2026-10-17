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
import app.fuelwise.feature.garage.PENDING_DELETE_VEHICLE_ID
import app.fuelwise.feature.garage.VehicleDetailRoute
import app.fuelwise.feature.garage.VehicleEditorRoute
import app.fuelwise.feature.garage.garageScreen
import app.fuelwise.feature.garage.vehicleDetailScreen
import app.fuelwise.feature.garage.vehicleEditorScreen
import app.fuelwise.feature.home.HomeRoute
import app.fuelwise.feature.home.homeScreen
import app.fuelwise.feature.insights.insightsScreen
import app.fuelwise.feature.logbook.logbookScreen
import app.fuelwise.feature.onboarding.HistoryRoute
import app.fuelwise.feature.onboarding.PrivacyRoute
import app.fuelwise.feature.onboarding.WelcomeRoute
import app.fuelwise.feature.onboarding.historyScreen
import app.fuelwise.feature.onboarding.privacyScreen
import app.fuelwise.feature.onboarding.welcomeScreen

/** App shell: bottom nav + FAB around a single NavHost. Feature modules only see callbacks, never each other. */
@Composable
fun FuelwiseApp(startWithOnboarding: Boolean, navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val destinations = TopLevelDestination.entries
    val selectedIndex = destinations.indexOfFirst { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
    }
    // The bar belongs to the four tabs; onboarding, detail and editor screens get the full height.
    val showBottomBar = selectedIndex >= 0

    // The add-entry sheet (F-02) arrives in M3; until then "+" and "Log fuel" have nothing to open.
    val openAddEntry: () -> Unit = {}
    val openVehicleEditor: () -> Unit = { navController.navigate(VehicleEditorRoute()) }

    Scaffold(
        containerColor = FwTheme.colors.bg,
        bottomBar = {
            if (showBottomBar) {
                FwBottomBar(
                    items = destinations.map { FwNavItem(stringResource(it.label), it.icon, it.selectedIcon) },
                    selectedIndex = selectedIndex.coerceAtLeast(0),
                    onSelect = { index -> navController.navigateToTopLevel(destinations[index]) },
                    onFabClick = openAddEntry,
                    fabContentDescription = stringResource(R.string.nav_add_entry),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startWithOnboarding) WelcomeRoute else HomeRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            welcomeScreen(onGetStarted = { navController.navigate(VehicleEditorRoute(onboarding = true)) })
            historyScreen(onBack = navController::popBackStack, onContinue = { navController.navigate(PrivacyRoute) })
            privacyScreen(
                onBack = navController::popBackStack,
                onDone = {
                    // Onboarding leaves the back stack: Back from Home exits the app.
                    navController.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
                },
            )
            homeScreen(onLogFuel = openAddEntry, onAddVehicle = openVehicleEditor)
            logbookScreen(onPrimaryAction = openAddEntry)
            insightsScreen(onPrimaryAction = openAddEntry)
            garageScreen(
                onOpenVehicle = { id -> navController.navigate(VehicleDetailRoute(id)) },
                onAddVehicle = openVehicleEditor,
            )
            vehicleEditorScreen(
                onBack = navController::popBackStack,
                onSaved = { _, onboarding ->
                    if (onboarding) navController.navigate(HistoryRoute) else navController.popBackStack()
                },
            )
            vehicleDetailScreen(
                onBack = navController::popBackStack,
                onEdit = { id -> navController.navigate(VehicleEditorRoute(vehicleId = id)) },
                onDelete = { id ->
                    // Garage performs the delete so it can show the undo snackbar.
                    navController.popBackStack()
                    navController.currentBackStackEntry?.savedStateHandle?.set(PENDING_DELETE_VEHICLE_ID, id)
                },
            )
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

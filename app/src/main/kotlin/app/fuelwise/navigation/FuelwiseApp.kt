package app.fuelwise.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.fuelwise.feature.expense.ExpenseRoute
import app.fuelwise.feature.expense.expenseSheet
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
import app.fuelwise.feature.refuel.RefuelRoute
import app.fuelwise.feature.refuel.refuelSheet

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

    val openAddEntry: () -> Unit = { navController.navigate(RefuelRoute()) }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
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
                    onFabLongClick = { quickAddOpen = true },
                    fabLongClickLabel = stringResource(R.string.nav_quick_add),
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
            refuelSheet(onClose = navController::popBackStack)
            expenseSheet(onClose = navController::popBackStack)
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
    if (quickAddOpen) {
        QuickAddSheet(
            onPick = { pick ->
                quickAddOpen = false
                when (pick) {
                    QuickAdd.Fuel -> navController.navigate(RefuelRoute())
                    QuickAdd.Expense -> navController.navigate(ExpenseRoute())
                    QuickAdd.Toll -> navController.navigate(ExpenseRoute(toll = true))
                }
            },
            onDismiss = { quickAddOpen = false },
        )
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

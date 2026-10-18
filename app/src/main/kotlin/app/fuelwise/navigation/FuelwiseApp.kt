package app.fuelwise.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.notifications.NotificationRoutes
import app.fuelwise.feature.backup.BackupRoute
import app.fuelwise.feature.backup.backupScreen
import app.fuelwise.feature.dataimport.ImportRoute
import app.fuelwise.feature.dataimport.importScreen
import app.fuelwise.feature.documents.DocumentEditorRoute
import app.fuelwise.feature.documents.DocumentViewRoute
import app.fuelwise.feature.documents.DocumentsRoute
import app.fuelwise.feature.documents.documentEditorScreen
import app.fuelwise.feature.documents.documentViewScreen
import app.fuelwise.feature.documents.documentsScreen
import app.fuelwise.feature.expense.ExpenseRoute
import app.fuelwise.feature.expense.expenseSheet
import app.fuelwise.feature.fastag.FastagRoute
import app.fuelwise.feature.fastag.fastagScreen
import app.fuelwise.feature.garage.PENDING_DELETE_VEHICLE_ID
import app.fuelwise.feature.garage.VehicleDetailRoute
import app.fuelwise.feature.garage.VehicleEditorRoute
import app.fuelwise.feature.garage.garageScreen
import app.fuelwise.feature.garage.list.GarageLinks
import app.fuelwise.feature.garage.vehicleDetailScreen
import app.fuelwise.feature.garage.vehicleEditorScreen
import app.fuelwise.feature.home.HomeActions
import app.fuelwise.feature.home.HomeRoute
import app.fuelwise.feature.home.homeScreen
import app.fuelwise.feature.insights.RecapRoute
import app.fuelwise.feature.insights.chartDetailScreen
import app.fuelwise.feature.insights.insightsScreen
import app.fuelwise.feature.insights.recapScreen
import app.fuelwise.feature.logbook.EntryDetailRoute
import app.fuelwise.feature.logbook.entryDetailScreen
import app.fuelwise.feature.logbook.logbookScreen
import app.fuelwise.feature.onboarding.HistoryRoute
import app.fuelwise.feature.onboarding.PrivacyRoute
import app.fuelwise.feature.onboarding.WelcomeRoute
import app.fuelwise.feature.onboarding.historyScreen
import app.fuelwise.feature.onboarding.privacyScreen
import app.fuelwise.feature.onboarding.welcomeScreen
import app.fuelwise.feature.refuel.RefuelRoute
import app.fuelwise.feature.refuel.refuelSheet
import app.fuelwise.feature.reminders.ReminderEditorRoute
import app.fuelwise.feature.reminders.RemindersRoute
import app.fuelwise.feature.reminders.reminderEditorScreen
import app.fuelwise.feature.reminders.remindersScreen

/** App shell: bottom nav + FAB around a single NavHost. Feature modules only see callbacks, never each other. */
@Composable
fun FuelwiseApp(
    startWithOnboarding: Boolean,
    openRequest: OpenRequest? = null,
    onOpenHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // A notification tap (core:notifications) asks for a screen; open it once the graph exists.
    LaunchedEffect(openRequest) {
        val route: Any = when (openRequest?.target) {
            NotificationRoutes.OPEN_REMINDERS -> RemindersRoute
            NotificationRoutes.OPEN_RECAP -> RecapRoute()
            NotificationRoutes.OPEN_FASTAG -> FastagRoute
            else -> return@LaunchedEffect
        }
        if (!startWithOnboarding) navController.navigate(route)
        onOpenHandled()
    }
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
            welcomeScreen(
                onGetStarted = { navController.navigate(VehicleEditorRoute(onboarding = true)) },
                onRestore = { navController.navigate(BackupRoute) },
            )
            historyScreen(
                onBack = navController::popBackStack,
                onContinue = { navController.navigate(PrivacyRoute) },
                onImport = { navController.navigate(ImportRoute) },
            )
            privacyScreen(
                onBack = navController::popBackStack,
                onDone = {
                    // Onboarding leaves the back stack: Back from Home exits the app.
                    navController.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
                },
            )
            homeScreen(
                HomeActions(
                    onLogFuel = openAddEntry,
                    onAddVehicle = openVehicleEditor,
                    onOpenEntry = { source, id -> navController.navigate(EntryDetailRoute(source.name, id)) },
                    onOpenLogbook = { navController.navigateToTopLevel(TopLevelDestination.Logbook) },
                    onOpenReminders = { navController.navigate(RemindersRoute) },
                    onOpenReminder = { id -> navController.navigate(ReminderEditorRoute(reminderId = id)) },
                ),
            )
            refuelSheet(onClose = navController::popBackStack)
            expenseSheet(onClose = navController::popBackStack)
            logbookScreen(
                onAddEntry = openAddEntry,
                onOpenEntry = { source, id -> navController.navigate(EntryDetailRoute(source.name, id)) },
                onEditEntry = { source, id -> navController.navigate(source.editRoute(id)) },
            )
            entryDetailScreen(
                onBack = navController::popBackStack,
                onEdit = { source, id -> navController.navigate(source.editRoute(id)) },
            )
            insightsScreen(
                onAddEntry = openAddEntry,
                onOpenChart = { navController.navigate(it) },
                onOpenRecap = { navController.navigate(RecapRoute()) },
            )
            chartDetailScreen(onBack = navController::popBackStack)
            recapScreen(onBack = navController::popBackStack)
            garageScreen(
                onOpenVehicle = { id -> navController.navigate(VehicleDetailRoute(id)) },
                onAddVehicle = openVehicleEditor,
                links = GarageLinks(
                    onReminders = { navController.navigate(RemindersRoute) },
                    onDocuments = { navController.navigate(DocumentsRoute) },
                    onFastag = { navController.navigate(FastagRoute) },
                    onBackup = { navController.navigate(BackupRoute) },
                    onImport = { navController.navigate(ImportRoute) },
                ),
            )
            remindersScreen(
                onBack = navController::popBackStack,
                onAdd = { navController.navigate(ReminderEditorRoute()) },
                onOpen = { id -> navController.navigate(ReminderEditorRoute(reminderId = id)) },
            )
            reminderEditorScreen(onClose = navController::popBackStack)
            documentsScreen(
                onBack = navController::popBackStack,
                onAdd = { navController.navigate(DocumentEditorRoute()) },
                onOpen = { id -> navController.navigate(DocumentViewRoute(id)) },
            )
            documentEditorScreen(onClose = navController::popBackStack)
            documentViewScreen(
                onBack = navController::popBackStack,
                onEdit = { id -> navController.navigate(DocumentEditorRoute(documentId = id)) },
            )
            backupScreen(
                onBack = navController::popBackStack,
                // Restored data replaces everything, onboarding included: start over on Home.
                onRestored = { navController.navigate(HomeRoute) { popUpTo(0) { inclusive = true } } },
            )
            importScreen(onBack = navController::popBackStack)
            fastagScreen(
                onBack = navController::popBackStack,
                onAddToll = { vehicleId -> navController.navigate(ExpenseRoute(vehicleId = vehicleId, toll = true)) },
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

private fun EntrySource.editRoute(id: String): Any = when (this) {
    EntrySource.Refuel -> RefuelRoute(refuelId = id)
    EntrySource.Expense -> ExpenseRoute(expenseId = id)
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** A screen asked for from outside the app (a notification). [id] makes two taps on the same target distinct. */
data class OpenRequest(val target: String, val id: Long)

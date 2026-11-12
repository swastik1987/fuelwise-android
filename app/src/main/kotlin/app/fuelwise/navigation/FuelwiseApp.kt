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
import androidx.navigation.toRoute
import app.fuelwise.R
import app.fuelwise.core.designsystem.component.FwBottomBar
import app.fuelwise.core.designsystem.component.FwNavItem
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.reminder.ReminderCost
import app.fuelwise.core.domain.trip.FillUpPrefill
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.notifications.NotificationRoutes
import app.fuelwise.feature.backup.BackupRoute
import app.fuelwise.feature.backup.backupScreen
import app.fuelwise.feature.dataimport.ExportRoute
import app.fuelwise.feature.dataimport.ImportRoute
import app.fuelwise.feature.dataimport.exportScreen
import app.fuelwise.feature.dataimport.importScreen
import app.fuelwise.feature.documents.DocumentEditorRoute
import app.fuelwise.feature.documents.DocumentViewRoute
import app.fuelwise.feature.documents.DocumentsRoute
import app.fuelwise.feature.documents.documentEditorScreen
import app.fuelwise.feature.documents.documentViewScreen
import app.fuelwise.feature.documents.documentsScreen
import app.fuelwise.feature.expense.CategoriesRoute
import app.fuelwise.feature.expense.ExpenseRoute
import app.fuelwise.feature.expense.expenseSheet
import app.fuelwise.feature.fastag.FastagRoute
import app.fuelwise.feature.fastag.fastagScreen
import app.fuelwise.feature.garage.GarageRoute
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
import app.fuelwise.feature.prices.PricesRoute
import app.fuelwise.feature.prices.pricesScreen
import app.fuelwise.feature.refuel.RefuelRoute
import app.fuelwise.feature.refuel.SCAN_RESULT_KEY
import app.fuelwise.feature.refuel.refuelSheet
import app.fuelwise.feature.reminders.ReminderEditorRoute
import app.fuelwise.feature.reminders.RemindersRoute
import app.fuelwise.feature.reminders.reminderEditorScreen
import app.fuelwise.feature.reminders.remindersScreen
import app.fuelwise.feature.scan.ScanRoute
import app.fuelwise.feature.scan.scanScreen
import app.fuelwise.feature.settings.PrivatePlacesRoute
import app.fuelwise.feature.settings.SettingsLinks
import app.fuelwise.feature.settings.SettingsRoute
import app.fuelwise.feature.settings.UnitsRoute
import app.fuelwise.feature.settings.settingsScreens
import app.fuelwise.feature.trips.AutoTripSetupRoute
import app.fuelwise.feature.trips.BatterySaverRoute
import app.fuelwise.feature.trips.LiveTripRoute
import app.fuelwise.feature.trips.PENDING_DELETE_TRIP_ID
import app.fuelwise.feature.trips.TripDetailRoute
import app.fuelwise.feature.trips.TripEditorRoute
import app.fuelwise.feature.trips.TripSettingsRoute
import app.fuelwise.feature.trips.TripsLinks
import app.fuelwise.feature.trips.TripsRoute
import app.fuelwise.feature.trips.tripsScreens

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

            NotificationRoutes.OPEN_LIVE_TRIP -> LiveTripRoute

            NotificationRoutes.OPEN_PRICES -> PricesRoute()

            NotificationRoutes.OPEN_INSIGHTS -> TopLevelDestination.Insights.route

            NotificationRoutes.OPEN_TRIP_ODOMETER ->
                openRequest.arg?.let { TripDetailRoute(it, editOdometer = true) } ?: return@LaunchedEffect

            // Widgets, Quick Settings tiles and app shortcuts (PRD F-16).
            NotificationRoutes.OPEN_ADD_FUEL -> RefuelRoute(vehicleId = openRequest.arg)

            NotificationRoutes.OPEN_ADD_FUEL_PROMPT -> RefuelRoute(
                vehicleId = FillUpPrefill.decode(openRequest.arg)?.vehicleId,
                prompt = openRequest.arg,
            )

            NotificationRoutes.OPEN_ADD_EXPENSE -> ExpenseRoute()

            NotificationRoutes.OPEN_ADD_REMINDER_COST ->
                ReminderCost.decode(openRequest.arg)?.let {
                    ExpenseRoute(vehicleId = it.vehicleId, categoryId = it.categoryId, completionId = it.completionId)
                } ?: return@LaunchedEffect

            NotificationRoutes.OPEN_ADD_TRIP -> TripEditorRoute()

            NotificationRoutes.OPEN_START_TRIP -> {
                if (!startWithOnboarding) navController.navigateToTrips(startTrip = true)
                onOpenHandled()
                return@LaunchedEffect
            }

            NotificationRoutes.OPEN_AUTO_TRIP_SETUP -> AutoTripSetupRoute

            NotificationRoutes.OPEN_DOCUMENTS -> DocumentsRoute

            NotificationRoutes.OPEN_SCAN -> ScanRoute()

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
                    onOpenTrips = { navController.navigateToTopLevel(TopLevelDestination.Trips) },
                    onStartTrip = { navController.navigateToTrips(startTrip = true) },
                    onOpenPrices = { navController.navigate(PricesRoute()) },
                    onOpenGarage = { navController.navigate(GarageRoute) },
                    onFixBattery = { navController.navigate(BatterySaverRoute) },
                ),
            )
            refuelSheet(
                onClose = navController::popBackStack,
                onScan = { vehicleId -> navController.navigate(ScanRoute(vehicleId = vehicleId)) },
            )
            scanScreen(
                onBack = navController::popBackStack,
                onResult = { prefill -> navController.openRefuelWithScan(prefill.encode()) },
                onTypeInstead = { navController.openRefuelWithScan(null) },
            )
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
                    onBack = navController::popBackStack,
                    onReminders = { navController.navigate(RemindersRoute) },
                    onDocuments = { navController.navigate(DocumentsRoute) },
                    onFastag = { navController.navigate(FastagRoute) },
                    onPrices = { navController.navigate(PricesRoute()) },
                    onBackup = { navController.navigate(BackupRoute) },
                    onImport = { navController.navigate(ImportRoute) },
                    onExport = { navController.navigate(ExportRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
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
            pricesScreen(onBack = navController::popBackStack)
            exportScreen(onBack = navController::popBackStack)
            settingsScreens(
                SettingsLinks(
                    onBack = navController::popBackStack,
                    onUnits = { vehicleId -> navController.navigate(UnitsRoute(vehicleId)) },
                    onHomeCity = { navController.navigate(PricesRoute(pickCity = true)) },
                    onExport = { navController.navigate(ExportRoute) },
                    onBackup = { navController.navigate(BackupRoute) },
                    onImport = { navController.navigate(ImportRoute) },
                    onCategories = { navController.navigate(CategoriesRoute) },
                    onTripSettings = { navController.navigate(TripSettingsRoute) },
                    // Everything is gone, onboarding included: start over at Welcome with an empty back stack.
                    onDataDeleted = { navController.navigate(WelcomeRoute) { popUpTo(0) { inclusive = true } } },
                ),
            )
            tripsScreens(
                TripsLinks(
                    onBack = navController::popBackStack,
                    onAddManual = { navController.navigate(TripEditorRoute()) },
                    onOpenTrip = { id -> navController.navigate(TripDetailRoute(id)) },
                    onEditTrip = { id -> navController.navigate(TripEditorRoute(tripId = id)) },
                    onDeleteTrip = { id ->
                        // The list performs the delete so it can show the undo snackbar.
                        navController.popBackStack()
                        navController.currentBackStackEntry?.savedStateHandle?.set(PENDING_DELETE_TRIP_ID, id)
                    },
                    onAddCost = { vehicleId, tripId, toll ->
                        navController.navigate(ExpenseRoute(vehicleId = vehicleId, toll = toll, tripId = tripId))
                    },
                    onOpenCost = { id -> navController.navigate(ExpenseRoute(expenseId = id)) },
                    onOpenLive = { navController.navigate(LiveTripRoute) { launchSingleTop = true } },
                    onAutoSetup = { navController.navigate(AutoTripSetupRoute) },
                    onSettings = { navController.navigate(TripSettingsRoute) },
                    onPrivatePlaces = { navController.navigate(PrivatePlacesRoute) },
                    onBatterySaver = { navController.navigate(BatterySaverRoute) },
                    onLiveFinished = { id ->
                        if (id == null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(TripDetailRoute(id)) { popUpTo<LiveTripRoute> { inclusive = true } }
                        }
                    },
                ),
                onEditorClose = navController::popBackStack,
            )
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
                    QuickAdd.Trip -> navController.navigate(TripEditorRoute())
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

/**
 * The Trips tab with a GPS trip starting on arrival. A fresh entry (no state restore) so the screen sees
 * `startTrip`; the tab's earlier state is saved as with any tab switch.
 */
private fun NavHostController.navigateToTrips(startTrip: Boolean) {
    navigate(TripsRoute(startTrip = startTrip)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
    }
}

/**
 * A screen asked for from outside the app (a notification). [id] makes two taps on the same target distinct; [arg]
 * names the record, e.g. a trip id.
 */
data class OpenRequest(val target: String, val id: Long, val arg: String? = null)

/**
 * Hands a scan back to the Add fuel sheet that opened it, or opens one (the app shortcut starts on Scan). [scan] is
 * `ScanPrefill.encode()`; null means "type instead".
 */
private fun NavHostController.openRefuelWithScan(scan: String?) {
    val previous = previousBackStackEntry
    if (previous?.destination?.hasRoute<RefuelRoute>() == true) {
        if (scan != null) previous.savedStateHandle[SCAN_RESULT_KEY] = scan
        popBackStack()
    } else {
        val vehicleId = currentBackStackEntry?.toRoute<ScanRoute>()?.vehicleId
        navigate(RefuelRoute(vehicleId = vehicleId, scan = scan)) { popUpTo<ScanRoute> { inclusive = true } }
    }
}

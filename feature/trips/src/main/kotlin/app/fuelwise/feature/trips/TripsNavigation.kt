package app.fuelwise.feature.trips

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/** The trips list; [startTrip] starts a GPS trip on arrival (Home's "Start trip"), asking for location if needed. */
@Serializable
data class TripsRoute(val startTrip: Boolean = false)

/** Add a manual trip (no id) or edit one. */
@Serializable
data class TripEditorRoute(val tripId: String? = null)

/** A trip; [editOdometer] opens the odometer sheet straight away (from the post-trip prompt, PRD F-07.6). */
@Serializable
data class TripDetailRoute(val tripId: String, val editOdometer: Boolean = false)

/** The GPS trip recording now (design.md screen 18). */
@Serializable
data object LiveTripRoute

/** Automatic trips setup with the background-location disclosure (design.md screen 32, PRD F-21.3). */
@Serializable
data object AutoTripSetupRoute

/** Trip settings: automatic trips, location access, car Bluetooth, location privacy (moved from Settings). */
@Serializable
data object TripSettingsRoute

/** Phone-maker battery-saver fix (design.md screen 33, PRD F-21.7), from Home's amber pill or Settings. */
@Serializable
data object BatterySaverRoute

/**
 * Key on the Trips back-stack entry's SavedStateHandle: the detail screen hands back a trip id to delete, and the
 * list performs the delete so it can offer the 10-second undo.
 */
const val PENDING_DELETE_TRIP_ID = "pendingDeleteTripId"

/** Where the trip screens lead outside this module. */
data class TripsLinks(
    val onBack: () -> Unit = {},
    val onAddManual: () -> Unit = {},
    val onOpenTrip: (tripId: String) -> Unit = {},
    val onEditTrip: (tripId: String) -> Unit = {},
    val onDeleteTrip: (tripId: String) -> Unit = {},
    /** Opens the expense sheet linked to the trip; [toll] opens the toll variant. */
    val onAddCost: (vehicleId: String, tripId: String, toll: Boolean) -> Unit = { _, _, _ -> },
    val onOpenCost: (expenseId: String) -> Unit = {},
    val onOpenLive: () -> Unit = {},
    /** After Stop: the saved trip to open, or null when it was too short to keep. */
    val onLiveFinished: (tripId: String?) -> Unit = {},
    /** "Record trips automatically" → setup. */
    val onAutoSetup: () -> Unit = {},
    /** The gear on Trips. */
    val onSettings: () -> Unit = {},
    /** Private places (PRD F-21.6), a screen in feature:settings. */
    val onPrivatePlaces: () -> Unit = {},
    /** The battery-saver fix (PRD F-21.7). */
    val onBatterySaver: () -> Unit = {},
)

fun NavGraphBuilder.tripsScreens(links: TripsLinks, onEditorClose: () -> Unit) {
    composable<TripsRoute> { entry -> TripsScreen(links, startTrip = entry.toRoute<TripsRoute>().startTrip) }
    composable<TripEditorRoute> { TripEditorScreen(onClose = onEditorClose) }
    composable<TripDetailRoute> { TripDetailScreen(links) }
    composable<LiveTripRoute> { LiveTripScreen(links) }
    composable<AutoTripSetupRoute> { AutoTripSetupScreen(onClose = links.onBack) }
    composable<BatterySaverRoute> { BatterySaverScreen(onClose = links.onBack) }
    composable<TripSettingsRoute> { TripSettingsScreen(links) }
}

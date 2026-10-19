package app.fuelwise.feature.trips

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TripsRoute

/** Add a manual trip (no id) or edit one. */
@Serializable
data class TripEditorRoute(val tripId: String? = null)

/** A trip; [editOdometer] opens the odometer sheet straight away (from the post-trip prompt, PRD F-07.6). */
@Serializable
data class TripDetailRoute(val tripId: String, val editOdometer: Boolean = false)

/** The GPS trip recording now (design.md screen 18). */
@Serializable
data object LiveTripRoute

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
)

fun NavGraphBuilder.tripsScreens(links: TripsLinks, onEditorClose: () -> Unit) {
    composable<TripsRoute> { TripsScreen(links) }
    composable<TripEditorRoute> { TripEditorScreen(onClose = onEditorClose) }
    composable<TripDetailRoute> { TripDetailScreen(links) }
    composable<LiveTripRoute> { LiveTripScreen(links) }
}

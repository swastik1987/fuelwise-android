package app.fuelwise.feature.garage

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.fuelwise.feature.garage.detail.VehicleDetailScreen
import app.fuelwise.feature.garage.editor.VehicleEditorScreen
import app.fuelwise.feature.garage.list.GarageLinks
import app.fuelwise.feature.garage.list.GarageScreen
import kotlinx.serialization.Serializable

@Serializable
data object GarageRoute

/** Add a vehicle (no id) or edit one. [onboarding] = opened from first run, which continues after saving. */
@Serializable
data class VehicleEditorRoute(val vehicleId: String? = null, val onboarding: Boolean = false)

@Serializable
data class VehicleDetailRoute(val vehicleId: String)

/**
 * Key on the Garage back-stack entry's SavedStateHandle: the detail screen hands back a vehicle id to delete,
 * and Garage performs the delete so it can offer the 10-second undo (PRD F-01 AC).
 */
const val PENDING_DELETE_VEHICLE_ID = "pendingDeleteVehicleId"

fun NavGraphBuilder.garageScreen(
    onOpenVehicle: (String) -> Unit,
    onAddVehicle: () -> Unit,
    onReminders: () -> Unit,
    onDocuments: () -> Unit,
    onFastag: () -> Unit,
) {
    composable<GarageRoute> {
        GarageScreen(
            onOpenVehicle = onOpenVehicle,
            onAddVehicle = onAddVehicle,
            links = GarageLinks(onReminders, onDocuments, onFastag),
        )
    }
}

fun NavGraphBuilder.vehicleEditorScreen(onBack: () -> Unit, onSaved: (vehicleId: String, onboarding: Boolean) -> Unit) {
    composable<VehicleEditorRoute> { VehicleEditorScreen(onBack = onBack, onSaved = onSaved) }
}

fun NavGraphBuilder.vehicleDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit) {
    composable<VehicleDetailRoute> { VehicleDetailScreen(onBack = onBack, onEdit = onEdit, onDelete = onDelete) }
}

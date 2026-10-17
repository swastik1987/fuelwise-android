package app.fuelwise.feature.refuel

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * Add or edit a fill-up. The sheet becomes "Add charge" for an EV tank. [vehicleId] defaults to the selected
 * vehicle; [refuelId] opens an existing entry for editing.
 */
@Serializable
data class RefuelRoute(val vehicleId: String? = null, val refuelId: String? = null)

fun NavGraphBuilder.refuelSheet(onClose: () -> Unit) {
    composable<RefuelRoute> { RefuelScreen(onClose = onClose) }
}

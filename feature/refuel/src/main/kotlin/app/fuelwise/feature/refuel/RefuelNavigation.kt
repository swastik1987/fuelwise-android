package app.fuelwise.feature.refuel

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * Add or edit a fill-up. The sheet becomes "Add charge" for an EV tank. [vehicleId] defaults to the selected
 * vehicle; [refuelId] opens an existing entry for editing. [scan] carries values confirmed on the scan screen
 * (`ScanPrefill.encode()`, PRD F-15); the app may also set it later on this entry's saved state under
 * [SCAN_RESULT_KEY].
 */
@Serializable
data class RefuelRoute(
    val vehicleId: String? = null,
    val refuelId: String? = null,
    val scan: String? = null,
    /** From a fill-up prompt (PRD F-21.4): `FillUpPrefill.encode()`. */
    val prompt: String? = null,
)

/** Saved-state key for a scan result handed back to an open Add fuel sheet. Same name as [RefuelRoute.scan]. */
const val SCAN_RESULT_KEY = "scan"

fun NavGraphBuilder.refuelSheet(onClose: () -> Unit, onScan: (vehicleId: String?) -> Unit = {}) {
    composable<RefuelRoute> { RefuelScreen(onClose = onClose, onScan = onScan) }
}

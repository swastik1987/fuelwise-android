package app.fuelwise.feature.refuel

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.Serializable

/**
 * Add or edit a fill-up. The sheet becomes "Add charge" for an EV tank. [vehicleId] defaults to the selected
 * vehicle; [refuelId] opens an existing entry for editing. [scan] carries values confirmed on the scan screen
 * (`ScanPrefill.encode()`, PRD F-15) when the scan screen opens this sheet; a scan made from the open sheet's banner
 * comes back on this entry's saved state under [SCAN_RESULT_KEY].
 */
@Serializable
data class RefuelRoute(
    val vehicleId: String? = null,
    val refuelId: String? = null,
    val scan: String? = null,
    /** From a fill-up prompt (PRD F-21.4): `FillUpPrefill.encode()`. */
    val prompt: String? = null,
)

/**
 * Key on the Add fuel entry's `NavBackStackEntry.savedStateHandle` for a scan handed back to the open sheet. That
 * handle is not the ViewModel's own, so the destination forwards it ([deliverScanResults]). Distinct from the route's
 * `scan` argument, which both handles also carry, so a route scan is never applied twice.
 */
const val SCAN_RESULT_KEY = "scanResult"

fun NavGraphBuilder.refuelSheet(onClose: () -> Unit, onScan: (vehicleId: String?) -> Unit = {}) {
    composable<RefuelRoute> { entry ->
        val viewModel: RefuelViewModel = hiltViewModel()
        LaunchedEffect(entry) { entry.savedStateHandle.deliverScanResults(viewModel::onScanResult) }
        RefuelScreen(onClose = onClose, onScan = onScan, viewModel = viewModel)
    }
}

/** Hands each scan result set under [SCAN_RESULT_KEY] to [onResult] once, then clears it. */
internal suspend fun SavedStateHandle.deliverScanResults(onResult: (String) -> Unit) {
    getStateFlow<String?>(SCAN_RESULT_KEY, null).filterNotNull().collect { text ->
        set(SCAN_RESULT_KEY, null)
        onResult(text)
    }
}

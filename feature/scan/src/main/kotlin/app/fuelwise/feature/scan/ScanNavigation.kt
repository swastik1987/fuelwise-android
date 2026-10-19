package app.fuelwise.feature.scan

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.fuelwise.core.model.ScanPrefill
import kotlinx.serialization.Serializable

/** The three things the camera can read (design.md screen 9 mode switcher). */
enum class ScanMode { PumpMeter, Receipt, Odometer }

/**
 * Scan screen (PRD F-15, design.md screen 9). Opened from the "Scan pump meter" banner on Add fuel, and the target for
 * the "Scan pump meter" app shortcut (PRD F-16). [mode] is a [ScanMode] name; [vehicleId] defaults to the selected
 * vehicle and only tunes the reading (last price paid, last odometer).
 */
@Serializable
data class ScanRoute(val mode: String = ScanMode.PumpMeter.name, val vehicleId: String? = null)

/**
 * [onResult] gets the values the user confirmed; the app hands them to Add fuel ([ScanPrefill.encode]).
 * [onTypeInstead] opens Add fuel without them.
 */
fun NavGraphBuilder.scanScreen(onBack: () -> Unit, onResult: (ScanPrefill) -> Unit, onTypeInstead: () -> Unit) {
    composable<ScanRoute> { ScanScreen(onBack = onBack, onResult = onResult, onTypeInstead = onTypeInstead) }
}

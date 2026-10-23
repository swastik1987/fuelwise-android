package app.fuelwise.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.fuelwise.feature.settings.units.UnitsScreen
import kotlinx.serialization.Serializable

/** Settings (design.md screen 29), opened from Garage → gear. */
@Serializable
data object SettingsRoute

/** Units: the app-wide default, or one vehicle's own units when [vehicleId] is set (PRD F-18). */
@Serializable
data class UnitsRoute(val vehicleId: String? = null)

/**
 * Where Settings rows lead outside this module; the app shell wires them. A null link hides its row, for screens that
 * other features haven't shipped yet.
 */
data class SettingsLinks(
    val onBack: () -> Unit = {},
    val onUnits: (vehicleId: String?) -> Unit = {},
    /** "Home city" → the Fuel prices screen (PRD F-09). */
    val onHomeCity: (() -> Unit)? = null,
    /** "Export & reports" (PRD F-13). */
    val onExport: (() -> Unit)? = null,
    val onBackup: () -> Unit = {},
    val onImport: () -> Unit = {},
    /** "Automatic trips" while off → setup (design.md screen 32). Null hides the row. */
    val onAutoTripSetup: (() -> Unit)? = null,
    /** After "Delete all data": start over at onboarding. */
    val onDataDeleted: () -> Unit = {},
)

fun NavGraphBuilder.settingsScreens(links: SettingsLinks) {
    composable<SettingsRoute> { SettingsScreen(links) }
    composable<UnitsRoute> { UnitsScreen(onBack = links.onBack, onOpenVehicle = { links.onUnits(it) }) }
}

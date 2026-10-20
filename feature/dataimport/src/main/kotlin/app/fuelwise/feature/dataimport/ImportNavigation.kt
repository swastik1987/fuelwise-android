package app.fuelwise.feature.dataimport

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ImportRoute

fun NavGraphBuilder.importScreen(onBack: () -> Unit) {
    composable<ImportRoute> { ImportScreen(onBack = onBack) }
}

/** Export & reports (PRD F-13.1, F-13.3). Garage links here; Settings → Import / Export can too. */
@Serializable
data object ExportRoute

fun NavGraphBuilder.exportScreen(onBack: () -> Unit) {
    composable<ExportRoute> { ExportScreen(onBack = onBack) }
}

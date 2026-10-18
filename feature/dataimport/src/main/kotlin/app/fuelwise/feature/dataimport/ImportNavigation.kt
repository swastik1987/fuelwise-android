package app.fuelwise.feature.dataimport

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ImportRoute

fun NavGraphBuilder.importScreen(onBack: () -> Unit) {
    composable<ImportRoute> { ImportScreen(onBack = onBack) }
}

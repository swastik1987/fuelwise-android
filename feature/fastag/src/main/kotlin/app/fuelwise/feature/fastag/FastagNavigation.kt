package app.fuelwise.feature.fastag

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object FastagRoute

fun NavGraphBuilder.fastagScreen(onBack: () -> Unit, onAddToll: (vehicleId: String) -> Unit) {
    composable<FastagRoute> { FastagScreen(onBack = onBack, onAddToll = onAddToll) }
}

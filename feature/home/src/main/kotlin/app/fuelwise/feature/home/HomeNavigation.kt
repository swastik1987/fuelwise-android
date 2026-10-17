package app.fuelwise.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.fuelwise.core.model.EntrySource
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(
    onLogFuel: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenEntry: (EntrySource, String) -> Unit,
    onOpenLogbook: () -> Unit,
) {
    composable<HomeRoute> { HomeScreen(HomeActions(onLogFuel, onAddVehicle, onOpenEntry, onOpenLogbook)) }
}

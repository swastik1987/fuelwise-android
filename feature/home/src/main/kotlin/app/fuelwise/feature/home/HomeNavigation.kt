package app.fuelwise.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(onLogFuel: () -> Unit) {
    composable<HomeRoute> { HomeScreen(onLogFuel = onLogFuel) }
}

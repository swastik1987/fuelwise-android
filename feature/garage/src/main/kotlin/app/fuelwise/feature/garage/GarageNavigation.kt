package app.fuelwise.feature.garage

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object GarageRoute

fun NavGraphBuilder.garageScreen(onPrimaryAction: () -> Unit) {
    composable<GarageRoute> { GarageScreen(onPrimaryAction = onPrimaryAction) }
}

package app.fuelwise.feature.insights

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object InsightsRoute

fun NavGraphBuilder.insightsScreen(onPrimaryAction: () -> Unit) {
    composable<InsightsRoute> { InsightsScreen(onPrimaryAction = onPrimaryAction) }
}

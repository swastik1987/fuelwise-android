package app.fuelwise.feature.prices

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/**
 * Fuel prices (design.md screen 25). [pickCity] opens the city picker on arrival: Settings' "Home city" row
 * navigates here with it set.
 */
@Serializable
data class PricesRoute(val pickCity: Boolean = false)

fun NavGraphBuilder.pricesScreen(onBack: () -> Unit) {
    composable<PricesRoute> { entry ->
        PricesScreen(onBack = onBack, pickCity = entry.toRoute<PricesRoute>().pickCity)
    }
}

package app.fuelwise.feature.logbook

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LogbookRoute

fun NavGraphBuilder.logbookScreen(onPrimaryAction: () -> Unit) {
    composable<LogbookRoute> { LogbookScreen(onPrimaryAction = onPrimaryAction) }
}

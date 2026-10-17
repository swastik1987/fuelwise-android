package app.fuelwise.feature.backup

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object BackupRoute

/** [onRestored] runs after a successful restore, so the app can start over on Home with the restored data. */
fun NavGraphBuilder.backupScreen(onBack: () -> Unit, onRestored: () -> Unit) {
    composable<BackupRoute> { BackupScreen(onBack = onBack, onRestored = onRestored) }
}

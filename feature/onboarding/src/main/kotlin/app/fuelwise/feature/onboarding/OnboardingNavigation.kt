package app.fuelwise.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** First run: Welcome → (app opens the vehicle editor) → History → Privacy → Home. Permissions are never asked here. */
@Serializable
data object WelcomeRoute

@Serializable
data object HistoryRoute

@Serializable
data object PrivacyRoute

fun NavGraphBuilder.welcomeScreen(onGetStarted: () -> Unit, onRestore: () -> Unit) {
    composable<WelcomeRoute> { WelcomeScreen(onGetStarted = onGetStarted, onRestore = onRestore) }
}

fun NavGraphBuilder.historyScreen(onBack: () -> Unit, onContinue: () -> Unit, onImport: () -> Unit) {
    composable<HistoryRoute> { HistoryScreen(onBack = onBack, onContinue = onContinue, onImport = onImport) }
}

fun NavGraphBuilder.privacyScreen(onBack: () -> Unit, onDone: () -> Unit) {
    composable<PrivacyRoute> { PrivacyScreen(onBack = onBack, onDone = onDone) }
}

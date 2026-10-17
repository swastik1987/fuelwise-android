package app.fuelwise.feature.reminders

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object RemindersRoute

/** Add or edit a reminder. [kind] preselects a preset (a [app.fuelwise.core.model.ReminderKind] name). */
@Serializable
data class ReminderEditorRoute(val reminderId: String? = null, val vehicleId: String? = null, val kind: String? = null)

fun NavGraphBuilder.remindersScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    composable<RemindersRoute> { RemindersScreen(onBack = onBack, onAdd = onAdd, onOpen = onOpen) }
}

fun NavGraphBuilder.reminderEditorScreen(onClose: () -> Unit) {
    composable<ReminderEditorRoute> { ReminderEditorScreen(onClose = onClose) }
}

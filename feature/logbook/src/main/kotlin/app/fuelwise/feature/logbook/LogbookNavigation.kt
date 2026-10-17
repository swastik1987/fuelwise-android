package app.fuelwise.feature.logbook

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.fuelwise.core.model.EntrySource
import kotlinx.serialization.Serializable

@Serializable
data object LogbookRoute

/** Entry detail (design.md screen 13). [source] is an [EntrySource] name. */
@Serializable
data class EntryDetailRoute(val source: String, val id: String)

fun NavGraphBuilder.logbookScreen(
    onAddEntry: () -> Unit,
    onOpenEntry: (EntrySource, String) -> Unit,
    onEditEntry: (EntrySource, String) -> Unit,
) {
    composable<LogbookRoute> {
        LogbookScreen(onAddEntry = onAddEntry, onOpenEntry = onOpenEntry, onEditEntry = onEditEntry)
    }
}

fun NavGraphBuilder.entryDetailScreen(onBack: () -> Unit, onEdit: (EntrySource, String) -> Unit) {
    composable<EntryDetailRoute> { EntryDetailScreen(onBack = onBack, onEdit = onEdit) }
}

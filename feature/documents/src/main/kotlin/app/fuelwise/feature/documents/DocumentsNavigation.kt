package app.fuelwise.feature.documents

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DocumentsRoute

/** Add or edit. [type] preselects a [app.fuelwise.core.model.DocumentType] name. */
@Serializable
data class DocumentEditorRoute(val documentId: String? = null, val type: String? = null)

@Serializable
data class DocumentViewRoute(val documentId: String)

fun NavGraphBuilder.documentsScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    composable<DocumentsRoute> { DocumentsScreen(onBack = onBack, onAdd = onAdd, onOpen = onOpen) }
}

fun NavGraphBuilder.documentEditorScreen(onClose: () -> Unit) {
    composable<DocumentEditorRoute> { DocumentEditorScreen(onClose = onClose) }
}

fun NavGraphBuilder.documentViewScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    composable<DocumentViewRoute> { DocumentViewScreen(onBack = onBack, onEdit = onEdit) }
}

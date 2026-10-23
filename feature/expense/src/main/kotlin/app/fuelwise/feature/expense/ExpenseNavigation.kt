package app.fuelwise.feature.expense

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * Add or edit an expense; [toll] opens the toll variant (plaza + FASTag/Cash). [tripId] links a new entry to a trip,
 * so its amount counts in the trip's cost (PRD F-07.1). [categoryId] picks a category to start with; [completionId]
 * links the saved expense to a reminder's "Done", from its "Add cost" (PRD F-11.4).
 */
@Serializable
data class ExpenseRoute(
    val vehicleId: String? = null,
    val expenseId: String? = null,
    val toll: Boolean = false,
    val tripId: String? = null,
    val categoryId: String? = null,
    val completionId: String? = null,
)

/** Expense categories: add your own, rename or hide any (PRD F-04.2). Opened from Settings. */
@Serializable
data object CategoriesRoute

fun NavGraphBuilder.expenseSheet(onClose: () -> Unit) {
    composable<ExpenseRoute> { ExpenseScreen(onClose = onClose) }
    composable<CategoriesRoute> { CategoriesScreen(onBack = onClose) }
}

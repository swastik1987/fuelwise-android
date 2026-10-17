package app.fuelwise.feature.expense

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Add or edit an expense; [toll] opens the toll variant (plaza + FASTag/Cash). */
@Serializable
data class ExpenseRoute(val vehicleId: String? = null, val expenseId: String? = null, val toll: Boolean = false)

fun NavGraphBuilder.expenseSheet(onClose: () -> Unit) {
    composable<ExpenseRoute> { ExpenseScreen(onClose = onClose) }
}

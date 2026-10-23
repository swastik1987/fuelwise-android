package app.fuelwise.feature.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.component.FwPhotoStrip
import app.fuelwise.core.designsystem.theme.FwTheme

/** Receipt or bill photos (PRD F-04.1, design.md screen 10). */
@Composable
internal fun ExpensePhotos(state: ExpenseFormState, actions: ExpenseActions) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
        Text(stringResource(R.string.expense_photos), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
        FwPhotoStrip(
            photos = state.photos,
            photoDescription = stringResource(R.string.expense_photo),
            onAdd = actions.onAddPhoto.takeIf { state.canAddPhoto },
            addLabel = stringResource(R.string.expense_photo_add),
            onRemove = actions.onRemovePhoto,
            removeDescription = stringResource(R.string.expense_photo_remove),
        )
        if (state.photoFailed) {
            Text(
                stringResource(R.string.expense_photo_failed),
                style = FwTheme.type.caption,
                color = FwTheme.colors.negative,
            )
        }
    }
}

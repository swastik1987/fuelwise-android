package app.fuelwise.feature.refuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPhotoStrip
import app.fuelwise.core.designsystem.theme.FwTheme

/** Receipt or pump photos under "More" (PRD F-02.1, design.md screen 6). */
@Composable
internal fun EntryPhotos(state: RefuelFormState, actions: RefuelActions) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
        FwOverline(stringResource(R.string.refuel_photos))
        FwPhotoStrip(
            photos = state.photos,
            photoDescription = stringResource(R.string.refuel_photo),
            onAdd = actions.onAddPhoto.takeIf { state.canAddPhoto },
            addLabel = stringResource(R.string.refuel_photo_add),
            onRemove = actions.onRemovePhoto,
            removeDescription = stringResource(R.string.refuel_photo_remove),
        )
        if (state.photoFailed) {
            Text(
                stringResource(R.string.refuel_photo_failed),
                style = FwTheme.type.caption,
                color = FwTheme.colors.negative,
            )
        }
    }
}

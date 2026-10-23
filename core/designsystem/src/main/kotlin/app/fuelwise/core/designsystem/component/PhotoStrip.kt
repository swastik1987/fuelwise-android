package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

/** One thumbnail in a [FwPhotoStrip]. */
data class FwPhoto(val key: String, val image: ImageBitmap)

/**
 * A row of photo thumbnails (receipts, pump meter, damage), as on the fill-up and expense sheets and entry detail
 * (design.md screens 6, 10 and 11). Pass [onAdd] for an "Add photo" tile and [onRemove] for a remove button on each;
 * leave both null for a read-only strip.
 */
@Composable
fun FwPhotoStrip(
    photos: List<FwPhoto>,
    photoDescription: String,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
    addLabel: String = "",
    onRemove: ((String) -> Unit)? = null,
    removeDescription: String = "",
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
    ) {
        photos.forEach { photo ->
            Box(Modifier.size(TILE)) {
                Image(
                    photo.image,
                    photoDescription,
                    Modifier.fillMaxSize().clip(FwTheme.radius.input),
                    contentScale = ContentScale.Crop,
                )
                if (onRemove != null) {
                    // A 48 dp target around a small badge in the corner.
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(FwTheme.size.minTouchTarget)
                            .clickable { onRemove(photo.key) }
                            .semantics {
                                role = Role.Button
                                contentDescription = removeDescription
                            },
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Icon(
                            painterResource(FwIcons.Close),
                            null,
                            tint = FwTheme.colors.bg,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(BADGE)
                                .background(FwTheme.colors.ink, CircleShape)
                                .padding(3.dp),
                        )
                    }
                }
            }
        }
        if (onAdd != null) {
            Column(
                Modifier
                    .size(TILE)
                    .clip(FwTheme.radius.input)
                    .background(FwTheme.colors.surface2)
                    .clickable(onClick = onAdd)
                    .semantics { role = Role.Button }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(FwIcons.Camera), null, tint = FwTheme.colors.ink2)
                Text(
                    addLabel,
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private val TILE = 88.dp
private val BADGE = 22.dp

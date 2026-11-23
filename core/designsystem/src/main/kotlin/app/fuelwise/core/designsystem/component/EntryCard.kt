package app.fuelwise.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.designsystem.theme.FwTint

/**
 * One slot tile of [FwEntryCard]: a value with an optional small unit, over a caption ("33.1 L" / "₹98.5 / L"); or,
 * with [icon] and no caption, an icon beside the value (an expense category).
 */
data class FwEntryTile(
    val value: String,
    val unit: String? = null,
    val caption: String? = null,
    @param:DrawableRes val icon: Int? = null,
    /** Tile 1 draws its tint; null uses the neutral tile. */
    val tint: FwTint? = null,
    /** Tile 1's caption is 11/500, the others 11/400. */
    val strongCaption: Boolean = false,
)

/**
 * The Logbook entry card (design "Logbook entry card — one shell, two variants", 23 Nov 2026): 88 dp, a header line
 * with the date, a subtitle, an optional secondary figure and the amount, then three equal tiles. Fuel and expense
 * entries share this shell so a mixed list stays even; only the slot contents change. It grows with large fonts.
 */
@Composable
fun FwEntryCard(
    date: String,
    subtitle: String?,
    amount: String,
    tiles: List<FwEntryTile>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = FwTheme.colors
    val border = colors.cardBorder.takeIf { it != Color.Transparent }?.let { BorderStroke(FwTheme.size.hairline, it) }
    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .heightIn(min = FwTheme.size.entryCardHeight)
                .padding(horizontal = FwTheme.spacing.entryCardH, vertical = FwTheme.spacing.entryCardV),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.entryGap),
        ) {
            Header(date, subtitle, secondary, amount)
            Row(horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.entryGap)) {
                tiles.forEach { Tile(it, Modifier.weight(1f)) }
            }
        }
    }
    // One TalkBack stop per card, read as a sentence; the tiles' fragments would be noise on their own.
    val semantics = modifier.fillMaxWidth().clearAndSetSemantics { this.contentDescription = contentDescription }
    if (onClick == null) {
        Surface(semantics, shape = FwTheme.radius.entryCard, color = colors.surface, border = border, content = body)
    } else {
        Surface(
            onClick = onClick,
            modifier = semantics,
            shape = FwTheme.radius.entryCard,
            color = colors.surface,
            border = border,
            content = body,
        )
    }
}

@Composable
private fun Header(date: String, subtitle: String?, secondary: String?, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = FwTheme.type.label, color = FwTheme.colors.ink, maxLines = 1)
            subtitle?.let {
                Text(
                    " · $it",
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        secondary?.let {
            Text(
                it,
                style = FwTheme.type.micro,
                color = FwTheme.colors.ink2,
                maxLines = 1,
                modifier = Modifier.padding(start = FwTheme.spacing.xs, end = FwTheme.spacing.entryGap),
            )
        }
        // Never truncated: the amount wins over the subtitle.
        Text(amount, style = FwTheme.type.amount, color = FwTheme.colors.ink, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Tile(tile: FwEntryTile, modifier: Modifier) {
    val tint = tile.tint ?: FwTheme.colors.tints.neutral
    val valueColor = if (tile.tint != null) tint.content else FwTheme.colors.ink
    Column(
        modifier
            .heightIn(min = FwTheme.size.entryTileHeight)
            .background(tint.container, FwTheme.radius.entryTile)
            .padding(horizontal = FwTheme.spacing.entryTileH, vertical = FwTheme.spacing.entryTileV),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = if (tile.icon != null) Alignment.CenterVertically else Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xxs),
        ) {
            tile.icon?.let {
                Icon(painterResource(it), null, tint = tint.content, modifier = Modifier.size(FwTheme.size.iconSmall))
            }
            Text(
                tile.value,
                style = FwTheme.type.tileValue,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            tile.unit?.let {
                Text(
                    it,
                    style = FwTheme.type.micro,
                    color = if (tile.tint != null) tint.content else FwTheme.colors.ink2,
                    maxLines = 1,
                )
            }
        }
        tile.caption?.let {
            Text(
                it,
                style = if (tile.strongCaption) FwTheme.type.microStrong else FwTheme.type.micro,
                color = if (tile.tint != null) tint.content else FwTheme.colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

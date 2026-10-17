package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * Logbook / recent row: 40 dp icon tile tinted at 14% of [tint], title + meta, amount and optional efficiency chip.
 * Grows past 64 dp at large font scales instead of clipping.
 */
@Composable
fun FwEntryRow(
    icon: Int,
    tint: Color,
    title: String,
    meta: String,
    amount: String,
    modifier: Modifier = Modifier,
    chip: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .heightIn(min = FwTheme.size.entryRowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        Box(
            modifier = Modifier
                .size(FwTheme.size.iconTile)
                .background(tint.copy(alpha = TILE_TINT_ALPHA), FwTheme.radius.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = FwTheme.spacing.xs)) {
            Text(title, style = FwTheme.type.body, color = FwTheme.colors.ink)
            Text(
                meta,
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(amount, style = FwTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = FwTheme.colors.ink)
            if (chip != null) {
                Box(
                    Modifier
                        .height(20.dp)
                        .background(FwTheme.colors.surface2, FwTheme.radius.pill)
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        chip,
                        style = FwTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                        color = FwTheme.colors.ink,
                    )
                }
            }
        }
    }
}

private const val TILE_TINT_ALPHA = 0.14f

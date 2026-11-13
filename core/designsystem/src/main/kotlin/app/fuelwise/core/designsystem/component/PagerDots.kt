package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * Page dots under a pager: the current page in accent, the rest in line colour. With more than [MAX_DOTS] pages it
 * shows a sliding window around [selected], with the edge dots smaller to hint at more. [description] is read by
 * TalkBack in place of the dots ("Month 1 of 12").
 */
@Composable
fun FwPagerDots(count: Int, selected: Int, description: String, modifier: Modifier = Modifier) {
    if (count < 2) return
    val first = (selected - MAX_DOTS / 2).coerceIn(0, maxOf(0, count - MAX_DOTS))
    val last = minOf(count, first + MAX_DOTS) - 1
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (page in first..last) {
            val edge = (page == first && first > 0) || (page == last && last < count - 1)
            Box(
                Modifier
                    .size(if (edge) 4.dp else 6.dp)
                    .background(if (page == selected) FwTheme.colors.accent else FwTheme.colors.line, CircleShape),
            )
        }
    }
}

private const val MAX_DOTS = 7

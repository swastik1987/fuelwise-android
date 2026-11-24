package app.fuelwise.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale from docs/design.md §4.3. */
@Immutable
object FwSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp
    val huge: Dp = 56.dp

    /** Logbook entry card: padding 10 vertical, 12 horizontal; 6 between rows and tiles; tile 5 by 8. */
    val entryCardV: Dp = 10.dp
    val entryCardH: Dp = 12.dp
    val entryGap: Dp = 6.dp
    val entryTileV: Dp = 5.dp
    val entryTileH: Dp = 8.dp

    /** Screen side gutter. */
    val gutter: Dp = 20.dp

    /** Card inner padding. */
    val cardPadding: Dp = 20.dp

    /** Vertical rhythm between sections. */
    val section: Dp = 32.dp
}

@Immutable
object FwRadius {
    val card = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val input = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(16.dp)
    val segment = RoundedCornerShape(14.dp)
    val segmentThumb = RoundedCornerShape(11.dp)
    val tile = RoundedCornerShape(12.dp)

    /** Logbook entry card and its three tiles (design 23 Nov 2026). */
    val entryCard = RoundedCornerShape(16.dp)
    val entryTile = RoundedCornerShape(10.dp)
    val fab = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(percent = 50)
}

@Immutable
object FwSize {
    val minTouchTarget: Dp = 48.dp
    val buttonHeight: Dp = 56.dp
    val chipHeight: Dp = 36.dp
    val entryRowHeight: Dp = 64.dp
    val entryCardHeight: Dp = 88.dp
    val entryTileHeight: Dp = 40.dp

    /** The drawn route on a Trips card (owner choice 24 Nov 2026, option B). */
    val routeThumb: Dp = 56.dp
    val routeStroke: Dp = 2.dp
    val routeDot: Dp = 3.dp
    val iconTile: Dp = 40.dp
    val icon: Dp = 24.dp
    val iconSmall: Dp = 16.dp
    val fab: Dp = 64.dp
    val navBarHeight: Dp = 72.dp
    val topBarHeight: Dp = 56.dp
    val hairline: Dp = 1.dp
}

/** Motion durations (ms) from docs/design.md §4.5. */
object FwMotion {
    const val MICRO = 120
    const val STANDARD = 220
    const val EMPHASIS = 360
}

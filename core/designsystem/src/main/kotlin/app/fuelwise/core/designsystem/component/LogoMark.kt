package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * The gauge mark (design/Fuelwise 8 - App Icon): an open ink arc with one accent needle. Drawn on a 36-unit
 * grid so it matches the SVG in the design file, and follows the theme's ink and accent.
 */
@Composable
fun FwLogoMark(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val ink = FwTheme.colors.ink
    val accent = FwTheme.colors.accent
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / GRID
        val stroke = Stroke(width = STROKE_UNITS * unit, cap = StrokeCap.Round)
        val radius = ARC_RADIUS * unit
        val center = Offset(CENTER * unit, CENTER * unit)
        // Arc from (7, 27) clockwise over the top to (29, 27): starts at ≈140.7°, sweeps ≈258.6°.
        drawArc(
            color = ink,
            startAngle = ARC_START_DEG,
            sweepAngle = ARC_SWEEP_DEG,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = stroke,
        )
        drawLine(
            color = accent,
            start = Offset(NEEDLE_START_X * unit, NEEDLE_START_Y * unit),
            end = Offset(NEEDLE_END_X * unit, NEEDLE_END_Y * unit),
            strokeWidth = STROKE_UNITS * unit,
            cap = StrokeCap.Round,
        )
    }
}

private const val GRID = 36f
private const val STROKE_UNITS = 3f
private const val ARC_RADIUS = 13f
private const val CENTER = 18f
private const val ARC_START_DEG = 140.7f
private const val ARC_SWEEP_DEG = 258.6f
private const val NEEDLE_START_X = 18f
private const val NEEDLE_START_Y = 20f
private const val NEEDLE_END_X = 25f
private const val NEEDLE_END_Y = 11f

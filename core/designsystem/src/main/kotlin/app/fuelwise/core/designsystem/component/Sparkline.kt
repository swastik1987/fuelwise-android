package app.fuelwise.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme

/** A 1.5 dp single-series line (metric tiles, price rows). Decorative: the tile's text carries the meaning. */
@Composable
fun FwSparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = FwTheme.colors.ink) {
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val step = size.width / (values.size - 1)
        val inset = 2.dp.toPx()
        val path = Path()
        values.forEachIndexed { i, v ->
            val point = Offset(i * step, inset + ((max - v) / span).toFloat() * (size.height - 2 * inset))
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

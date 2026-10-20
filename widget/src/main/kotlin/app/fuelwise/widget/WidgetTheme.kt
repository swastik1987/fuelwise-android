package app.fuelwise.widget

import androidx.glance.color.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.fuelwise.core.designsystem.theme.DarkFwColors
import app.fuelwise.core.designsystem.theme.FwColors
import app.fuelwise.core.designsystem.theme.FwTypography
import app.fuelwise.core.designsystem.theme.LightFwColors

/**
 * Design-system tokens (docs/design.md §4) for Glance, which draws RemoteViews rather than Compose UI. Each colour
 * follows the launcher's light/dark setting. Backgrounds are the neutral surface tokens, not the accent, so widgets sit
 * quietly on Material You wallpapers (design.md screen 30).
 */
internal object WidgetColors {
    val background = pair { surface }
    val tile = pair { surface2 }
    val ink = pair { ink }
    val ink2 = pair { ink2 }
    val accent = pair { accent }
    val onAccent = pair { onAccent }
    val accentSoft = pair { accentSoft }
    val warning = pair { warning }
    val negative = pair { negative }

    private fun pair(token: FwColors.() -> androidx.compose.ui.graphics.Color): ColorProvider =
        ColorProvider(day = LightFwColors.token(), night = DarkFwColors.token())
}

/** Type scale sizes from [FwTypography]; Glance has no custom fonts, so the system font draws them. */
internal object WidgetType {
    private val type = FwTypography()

    val value = TextStyle(color = WidgetColors.ink, fontSize = type.h2.fontSize, fontWeight = FontWeight.Medium)
    val label = TextStyle(color = WidgetColors.ink, fontSize = type.label.fontSize, fontWeight = FontWeight.Medium)
    val caption = TextStyle(color = WidgetColors.ink2, fontSize = type.caption.fontSize)
    val overline =
        TextStyle(color = WidgetColors.ink2, fontSize = type.overline.fontSize, fontWeight = FontWeight.Medium)
}

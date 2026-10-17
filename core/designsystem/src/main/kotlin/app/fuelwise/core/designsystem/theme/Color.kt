package app.fuelwise.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour tokens from docs/design.md §4.1. Read them through [FwTheme.colors]; never use raw colours in features. */
@Immutable
data class FwColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val positive: Color,
    val warning: Color,
    val negative: Color,
    /** Solid backdrop behind bottom sheets. */
    val scrim: Color,
    /** Card border: a hairline in light theme, none in dark. */
    val cardBorder: Color,
    val isDark: Boolean,
)

val LightFwColors = FwColors(
    bg = Color(0xFFF5F4F0),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEEEDE8),
    ink = Color(0xFF121212),
    ink2 = Color(0xFF5C5C58),
    ink3 = Color(0xFF8E8E88),
    line = Color(0xFFE4E2DC),
    accent = Color(0xFFFF5B1F),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFFE7DC),
    positive = Color(0xFF1F9D55),
    warning = Color(0xFFE8A317),
    negative = Color(0xFFD93025),
    scrim = Color(0xFF8C8B87),
    cardBorder = Color(0xFFE4E2DC),
    isDark = false,
)

val DarkFwColors = FwColors(
    bg = Color(0xFF0D0D0E),
    surface = Color(0xFF161618),
    surface2 = Color(0xFF1F1F22),
    ink = Color(0xFFF2F1ED),
    ink2 = Color(0xFFA6A5A0),
    ink3 = Color(0xFF6E6D69),
    line = Color(0xFF2A2A2E),
    accent = Color(0xFFFF6A33),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF3A1E14),
    positive = Color(0xFF3CCB7F),
    warning = Color(0xFFF2B84B),
    negative = Color(0xFFFF6B5E),
    scrim = Color(0xFF050506),
    cardBorder = Color.Transparent,
    isDark = true,
)

/** Energy colours: data only (chart series, fuel dots and tags), never buttons. */
object EnergyColors {
    val Petrol = Color(0xFFF2B705)
    val Diesel = Color(0xFF3A6FF7)
    val Cng = Color(0xFF18A866)
    val Lpg = Color(0xFF8E5CF6)
    val Electric = Color(0xFF00B3C7)
    val Premium = Color(0xFFE85D9E)
}

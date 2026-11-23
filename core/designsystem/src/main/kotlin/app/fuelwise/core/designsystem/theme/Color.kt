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
    /** Tinted tiles on Logbook cards: mileage status and expense categories. */
    val tints: FwTints,
    val isDark: Boolean,
)

/** A tile background and the text and icon colour on it. */
@Immutable
data class FwTint(val container: Color, val content: Color)

/**
 * Logbook card tints (design "Logbook entry card", 23 Nov 2026). Mileage status uses green and red only; expense
 * categories never do, so a tinted tile in the All list can't be misread as good or bad mileage.
 */
@Immutable
data class FwTints(
    val better: FwTint,
    val worse: FwTint,
    val neutral: FwTint,
    val upkeep: FwTint,
    val battery: FwTint,
    val paperwork: FwTint,
    val other: FwTint,
)

val LightFwTints = FwTints(
    better = FwTint(Color(0xFFEAF6EF), Color(0xFF1F7A55)),
    worse = FwTint(Color(0xFFFCEEEA), Color(0xFFB3402F)),
    neutral = FwTint(Color(0xFFF6F5F2), Color(0xFF4A4741)),
    upkeep = FwTint(Color(0xFFECEFF7), Color(0xFF3B4F7A)),
    battery = FwTint(Color(0xFFF1ECF8), Color(0xFF5B3F8C)),
    paperwork = FwTint(Color(0xFFF7F0E4), Color(0xFF7A5212)),
    other = FwTint(Color(0xFFEFEEEA), Color(0xFF4A4741)),
)

/** Dark: the same hues as dim containers with light text, keeping AA contrast on the dark surface. */
val DarkFwTints = FwTints(
    better = FwTint(Color(0xFF15281E), Color(0xFF7AD3A2)),
    worse = FwTint(Color(0xFF2E1916), Color(0xFFFF9A8A)),
    neutral = FwTint(Color(0xFF1F1F22), Color(0xFFD2D0CA)),
    upkeep = FwTint(Color(0xFF1A2032), Color(0xFFA9BAE2)),
    battery = FwTint(Color(0xFF241C33), Color(0xFFC6AEEC)),
    paperwork = FwTint(Color(0xFF2B2216), Color(0xFFE6C083)),
    other = FwTint(Color(0xFF222220), Color(0xFFD2D0CA)),
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
    tints = LightFwTints,
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
    tints = DarkFwTints,
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

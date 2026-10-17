package app.fuelwise.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.fuelwise.core.designsystem.R

/*
 * Type scale from docs/design.md §4.2. Geist is a variable font; each weight is a variation of one file.
 * Devanagari glyphs fall back to the system's Noto Sans Devanagari, which ships on Android.
 */

@OptIn(ExperimentalTextApi::class)
private fun geistWeight(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Geist = FontFamily(
    geistWeight(R.font.geist, 400),
    geistWeight(R.font.geist, 500),
    geistWeight(R.font.geist, 600),
)

/** Only for the odometer roller and pump-scan readout (and registration numbers in the mocks). */
val GeistMono = FontFamily(
    geistWeight(R.font.geist_mono, 400),
    geistWeight(R.font.geist_mono, 500),
    geistWeight(R.font.geist_mono, 600),
)

/** Tabular figures so digits line up everywhere. */
private const val TABULAR = "tnum"

private fun style(size: Int, lineHeight: Int, weight: Int, tracking: TextUnit = 0.em, family: FontFamily = Geist) =
    TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking,
        fontFeatureSettings = TABULAR,
    )

@Immutable
data class FwTypography(
    val displayXl: TextStyle = style(64, 68, 600, (-0.03).em),
    val display: TextStyle = style(48, 52, 600, (-0.02).em),
    val h1: TextStyle = style(30, 36, 600, (-0.01).em),
    val h2: TextStyle = style(22, 28, 600, (-0.005).em),
    val h3: TextStyle = style(18, 24, 600),
    val body: TextStyle = style(16, 24, 400),
    val bodyS: TextStyle = style(14, 20, 400),
    val label: TextStyle = style(14, 18, 500),
    val caption: TextStyle = style(12, 16, 400, 0.01.em),
    val overline: TextStyle = style(11, 14, 600, 0.06.em),
    val mono: TextStyle = style(26, 30, 500, family = GeistMono),
    val monoS: TextStyle = style(13, 18, 500, 0.04.em, family = GeistMono),
)

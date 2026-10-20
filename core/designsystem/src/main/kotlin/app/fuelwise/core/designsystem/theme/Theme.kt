package app.fuelwise.core.designsystem.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalFwColors = staticCompositionLocalOf { LightFwColors }
private val LocalFwTypography = staticCompositionLocalOf { FwTypography() }

/** Entry point for tokens inside composables: `FwTheme.colors.ink`, `FwTheme.type.body`. */
object FwTheme {
    val colors: FwColors
        @Composable @ReadOnlyComposable
        get() = LocalFwColors.current

    val type: FwTypography
        @Composable @ReadOnlyComposable
        get() = LocalFwTypography.current

    val spacing = FwSpacing
    val radius = FwRadius
    val size = FwSize
}

/**
 * App theme. Material 3 is mapped onto our tokens so stock components (text fields, dialogs) still look right,
 * but screens should read [FwTheme] directly rather than `MaterialTheme` defaults.
 */
@Composable
fun FuelwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val brand = if (darkTheme) DarkFwColors else LightFwColors
    val context = LocalContext.current
    // Material You (PRD F-18, off by default): Android 12+ only; older devices keep the brand palette.
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        remember(darkTheme, context) {
            brand.withDynamic(if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
        }
    } else {
        brand
    }
    val typography = FwTypography()
    CompositionLocalProvider(
        LocalFwColors provides colors,
        LocalFwTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            content = content,
        )
    }
}

/** Whether this device can show Material You colours (Android 12+); Settings hides the switch otherwise. */
@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Wallpaper colours replace the accent and the neutral surfaces. Ink, hairlines and the semantic colours (positive,
 * warning, negative) stay ours so text contrast and delta meanings never change.
 */
internal fun FwColors.withDynamic(scheme: ColorScheme): FwColors = copy(
    bg = scheme.surface,
    surface = scheme.surfaceContainerLowest,
    surface2 = scheme.surfaceContainerHigh,
    accent = scheme.primary,
    onAccent = scheme.onPrimary,
    accentSoft = scheme.primaryContainer,
)

private fun FwColors.toMaterialScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        secondary = ink,
        onSecondary = bg,
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surface2,
        onSurfaceVariant = ink2,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerLow = surface2,
        outline = ink3,
        outlineVariant = line,
        error = negative,
        onError = onAccent,
        scrim = scrim,
    )
}

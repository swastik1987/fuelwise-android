package app.fuelwise.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

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
fun FuelwiseTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkFwColors else LightFwColors
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

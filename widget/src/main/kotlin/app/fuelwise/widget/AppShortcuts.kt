package app.fuelwise.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.LightFwColors
import app.fuelwise.core.notifications.NotificationRoutes
import kotlin.math.roundToInt

/**
 * Launcher shortcuts (long-press the icon, PRD F-16): Add fuel, Scan pump meter, Start trip, Documents. Published at
 * runtime rather than in a shortcuts.xml so the intents name this build's package (debug has a suffix) and the labels
 * follow the app language. Republished only when they change: launchers rate-limit updates from the background.
 */
object AppShortcuts {
    fun publish(context: Context) {
        val shortcuts = listOf(
            Spec(ID_ADD_FUEL, R.string.shortcut_add_fuel, R.string.shortcut_add_fuel_long, FwIcons.Fuel, NotificationRoutes.OPEN_ADD_FUEL),
            // TODO(F-15): OPEN_SCAN opens the fill-up sheet until the Scan PR's route is merged into FuelwiseApp.
            Spec(ID_SCAN, R.string.shortcut_scan, R.string.shortcut_scan_long, FwIcons.Camera, NotificationRoutes.OPEN_SCAN),
            Spec(
                ID_START_TRIP,
                R.string.shortcut_start_trip,
                R.string.shortcut_start_trip_long,
                FwIcons.Route,
                NotificationRoutes.OPEN_START_TRIP,
            ),
            Spec(
                ID_DOCUMENTS,
                R.string.shortcut_documents,
                R.string.shortcut_documents_long,
                FwIcons.Document,
                NotificationRoutes.OPEN_DOCUMENTS,
            ),
        )
        val current = ShortcutManagerCompat.getDynamicShortcuts(context).associate { it.id to it.shortLabel.toString() }
        if (current == shortcuts.associate { it.id to context.getString(it.shortLabel) }) return
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts.mapIndexed { rank, spec -> spec.build(context, rank) })
    }

    private class Spec(
        val id: String,
        @StringRes val shortLabel: Int,
        @StringRes val longLabel: Int,
        @DrawableRes val icon: Int,
        val target: String,
    ) {
        fun build(context: Context, rank: Int): ShortcutInfoCompat = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(shortLabel))
            .setLongLabel(context.getString(longLabel))
            .setIcon(icon(context, icon))
            .setIntent(WidgetLinks.open(context, target))
            .setRank(rank)
            .build()
    }

    /** The design-system glyph in the accent colour on the soft accent, as an adaptive icon the launcher can mask. */
    private fun icon(context: Context, @DrawableRes res: Int): IconCompat {
        val size = (ADAPTIVE_ICON_DP * context.resources.displayMetrics.density).roundToInt()
        val bitmap: Bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(LightFwColors.accentSoft.toArgb())
        ContextCompat.getDrawable(context, res)?.mutate()?.apply {
            setTint(LightFwColors.accent.toArgb())
            // The glyph sits inside the adaptive icon's safe zone (the middle 2/3, then some breathing room).
            val inset = size * GLYPH_INSET
            setBounds(inset.roundToInt(), inset.roundToInt(), (size - inset).roundToInt(), (size - inset).roundToInt())
            draw(canvas)
        }
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    private const val ID_ADD_FUEL = "add_fuel"
    private const val ID_SCAN = "scan"
    private const val ID_START_TRIP = "start_trip"
    private const val ID_DOCUMENTS = "documents"

    /** Adaptive icons are 108 × 108 dp (Android's spec), of which the launcher shows the middle 72 dp. */
    private const val ADAPTIVE_ICON_DP = 108
    private const val GLYPH_INSET = 0.3f
}

package app.fuelwise.widget

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import app.fuelwise.core.designsystem.theme.FwSize
import app.fuelwise.core.designsystem.theme.FwSpacing

/** The widget's card: neutral surface, the launcher's own corner radius (Android 12+). */
@Composable
internal fun WidgetSurface(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    var base = GlanceModifier.fillMaxSize().appWidgetBackground().background(WidgetColors.background)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base = base.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    }
    Box(base.then(modifier).padding(FwSpacing.s), contentAlignment = Alignment.Center) { content() }
}

/** A 48 dp round icon button with a TalkBack label. */
@Composable
internal fun WidgetIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
    container: ColorProvider = WidgetColors.accentSoft,
    tint: ColorProvider = WidgetColors.accent,
) {
    var shape = GlanceModifier.size(FwSize.minTouchTarget).background(container)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) shape = shape.cornerRadius(FwSize.minTouchTarget / 2)
    Box(
        modifier = shape.then(modifier).clickable(onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            // The button carries the label; the icon itself is decoration.
            contentDescription = null,
            modifier = GlanceModifier.size(FwSize.icon),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

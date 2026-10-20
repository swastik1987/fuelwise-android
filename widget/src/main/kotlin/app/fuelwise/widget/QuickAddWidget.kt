package app.fuelwise.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwSize
import app.fuelwise.core.notifications.NotificationRoutes

/** Quick add 2×1 (PRD F-16, design.md screen 30): fuel, charge, expense and trip, each opening its sheet. */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val chargeVehicleId = context.widgetEntryPoint().data().vehicles().chargeVehicleId
        provideContent { QuickAddContent(chargeVehicleId) }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

/** [chargeVehicleId]: the EV "Charge" opens the sheet for; null opens the sheet's usual vehicle. */
@Composable
internal fun QuickAddContent(chargeVehicleId: String?) {
    val context = LocalContext.current
    WidgetSurface {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            QuickAddSlot(
                FwIcons.Fuel,
                R.string.widget_add_fuel,
                WidgetLinks.open(context, NotificationRoutes.OPEN_ADD_FUEL),
            )
            QuickAddSlot(
                FwIcons.Charge,
                R.string.widget_add_charge,
                WidgetLinks.open(context, NotificationRoutes.OPEN_ADD_FUEL, chargeVehicleId),
            )
            QuickAddSlot(
                FwIcons.Cash,
                R.string.widget_add_expense,
                WidgetLinks.open(context, NotificationRoutes.OPEN_ADD_EXPENSE),
            )
            QuickAddSlot(
                FwIcons.Route,
                R.string.widget_add_trip,
                WidgetLinks.open(context, NotificationRoutes.OPEN_ADD_TRIP),
            )
        }
    }
}

/** A quarter of the widget is the touch target, so it stays large however narrow the launcher makes the widget. */
@Composable
private fun androidx.glance.layout.RowScope.QuickAddSlot(@DrawableRes icon: Int, @StringRes label: Int, open: Intent) {
    val description = LocalContext.current.getString(label)
    Box(
        modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity(open))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        var tile = GlanceModifier.size(FwSize.iconTile).background(WidgetColors.accentSoft)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tile = tile.cornerRadius(FwSize.iconTile / 2)
        Box(tile, contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                modifier = GlanceModifier.size(FwSize.icon),
                colorFilter = ColorFilter.tint(WidgetColors.accent),
            )
        }
    }
}

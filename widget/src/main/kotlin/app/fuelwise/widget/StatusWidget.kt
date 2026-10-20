package app.fuelwise.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import app.fuelwise.core.designsystem.theme.FwSpacing
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.UnitFormat

/** Status 2×2 (PRD F-16, design.md screen 30): ₹/km this month, last mileage, next due item. Tap opens Home. */
class StatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = context.widgetEntryPoint().data().status()
        provideContent { StatusContent(model) }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}

/** [model] null: no vehicle yet. */
@Composable
internal fun StatusContent(model: StatusWidgetModel?) {
    val context = LocalContext.current
    val openLabel = context.getString(R.string.widget_open_app)
    val openApp = GlanceModifier.clickable(actionStartActivity(WidgetLinks.open(context)))
        .semantics { contentDescription = openLabel }
    WidgetSurface(openApp) {
        if (model == null) {
            Text(context.getString(R.string.widget_no_vehicle), style = WidgetType.label)
        } else {
            StatusBody(model)
        }
    }
}

@Composable
private fun StatusBody(model: StatusWidgetModel) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Text(
            listOf(model.vehicleName, context.getString(R.string.widget_cost_overline)).joinToString(" · "),
            style = WidgetType.overline,
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(FwSpacing.xxs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(costText(model), style = WidgetType.value, maxLines = 1)
            Spacer(GlanceModifier.width(FwSpacing.xxs))
            Text(context.getString(R.string.widget_per_km), style = WidgetType.caption, maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(mileageText(context, model), style = WidgetType.label, maxLines = 1)
            Spacer(GlanceModifier.width(FwSpacing.xs))
            Text(
                context.getString(if (model.isEstimate) R.string.widget_mileage_est else R.string.widget_mileage),
                style = WidgetType.caption,
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(FwSpacing.xxs))
        val due = model.nextDue
        Text(
            due?.let { dueText(context, it) } ?: context.getString(R.string.widget_nothing_due),
            style = when (due?.urgency) {
                Urgency.Overdue -> WidgetType.label.copy(color = WidgetColors.negative)
                Urgency.Due -> WidgetType.label.copy(color = WidgetColors.warning)
                null -> WidgetType.caption
                else -> WidgetType.label
            },
            maxLines = 1,
        )
    }
}

/** "₹4.82", masked when amounts are hidden; a dash before the month has a distance. */
internal fun costText(model: StatusWidgetModel): String = when {
    model.hideAmounts -> HIDDEN_AMOUNT
    model.costPerKm == null -> NO_VALUE
    else -> IndianNumberFormat.rupees(model.costPerKm)
}

private fun mileageText(context: Context, model: StatusWidgetModel): String {
    val km = model.lastKmPerUnit ?: return NO_VALUE
    // In the user's efficiency unit (PRD F-18): km/L, L/100 km and the kg and kWh variants.
    val figure = model.units.efficiency(km, model.unit ?: QuantityUnit.LITRE) ?: return NO_VALUE
    val label = context.getString(UnitFormat.label(figure.label))
    return context.getString(R.string.widget_mileage_value, IndianNumberFormat.decimal(figure.value, 1), label)
}

private fun dueText(context: Context, status: ReminderStatus): String {
    val title = status.reminder.title ?: context.getString(CareLabels.reminder(status.reminder.kind))
    val due = when (val label = dueLabel(status)) {
        DueLabel.Overdue -> context.getString(R.string.widget_due_overdue)
        DueLabel.Today -> context.getString(R.string.widget_due_today)
        is DueLabel.Days -> context.resources.getQuantityString(R.plurals.widget_due_days, label.days, label.days)
        is DueLabel.Km -> context.getString(R.string.widget_due_km, IndianNumberFormat.decimal(label.km, 0))
        null -> return title
    }
    return context.getString(R.string.widget_due_item, title, due)
}

/** As Home's privacy blur (PRD F-18 "Hide amounts"). */
internal const val HIDDEN_AMOUNT = "₹ •••"
private const val NO_VALUE = "—"

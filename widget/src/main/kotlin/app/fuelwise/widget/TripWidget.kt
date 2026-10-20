package app.fuelwise.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwSpacing
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.location.TripRecordingIntents
import app.fuelwise.core.notifications.NotificationRoutes
import app.fuelwise.core.ui.format.IndianNumberFormat

/** What the Trip controls widget shows. */
sealed interface TripWidgetModel {
    /** [startInPlace]: location is granted and there's a vehicle, so Start can record without opening the app. */
    data class Idle(val startInPlace: Boolean) : TripWidgetModel

    data class Recording(val distanceKm: Double, val paused: Boolean) : TripWidgetModel
}

fun tripWidgetModel(state: RecordingState, hasPermission: Boolean, hasVehicle: Boolean): TripWidgetModel =
    when (state) {
        is RecordingState.Active -> TripWidgetModel.Recording(state.summary.distanceKm, state.paused)
        RecordingState.Idle -> TripWidgetModel.Idle(startInPlace = hasPermission && hasVehicle)
    }

/**
 * Trip controls 2×1 (PRD F-16, design.md screen 30): Start / Stop with the live distance, driven by the trip recorder.
 * Start records straight away only when while-in-use location is already granted; otherwise it opens Trips, which asks
 * first. Background location is never requested (CLAUDE.md rule 6).
 */
class TripWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val recorder = entryPoint.recorder()
        val model = tripWidgetModel(
            recorder.state.value,
            recorder.hasPermission,
            entryPoint.data().vehicles().selected != null,
        )
        provideContent { TripContent(model) }
    }
}

class TripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TripWidget()
}

@Composable
internal fun TripContent(model: TripWidgetModel) {
    val context = LocalContext.current
    // A widget tap is one of the few ways Android lets a background app start a location foreground service with
    // while-in-use access, so Start and Stop go straight to the recording service.
    val start: Action = if ((model as? TripWidgetModel.Idle)?.startInPlace == true) {
        actionStartService(TripRecordingIntents.start(context), isForegroundService = true)
    } else {
        actionStartActivity(WidgetLinks.open(context, NotificationRoutes.OPEN_START_TRIP))
    }
    val recording = model as? TripWidgetModel.Recording
    val open = if (recording != null) {
        actionStartActivity(WidgetLinks.open(context, NotificationRoutes.OPEN_LIVE_TRIP))
    } else {
        start
    }
    val openLabel =
        context.getString(if (recording != null) R.string.widget_open_live_trip else R.string.widget_start_trip)
    val status = context.getString(
        when {
            recording == null -> R.string.widget_trip_idle
            recording.paused -> R.string.widget_trip_paused
            else -> R.string.widget_trip_recording
        },
    )
    WidgetSurface {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(open).semantics { contentDescription = openLabel }) {
                Text(status, style = WidgetType.overline, maxLines = 1)
                if (recording != null) {
                    Text(
                        context.getString(
                            R.string.widget_trip_distance,
                            IndianNumberFormat.decimal(recording.distanceKm, 1),
                        ),
                        style = WidgetType.value,
                        maxLines = 1,
                    )
                } else {
                    Text(context.getString(R.string.widget_start_trip), style = WidgetType.label, maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(FwSpacing.xs))
            WidgetIconButton(
                icon = if (recording != null) FwIcons.Stop else FwIcons.Play,
                label = context.getString(
                    if (recording !=
                        null
                    ) {
                        R.string.widget_stop_trip
                    } else {
                        R.string.widget_start_trip
                    },
                ),
                onClick = if (recording != null) {
                    actionStartService(TripRecordingIntents.stop(context), isForegroundService = true)
                } else {
                    start
                },
                container = WidgetColors.accent,
                tint = WidgetColors.onAccent,
            )
        }
    }
}

package app.fuelwise.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.ReminderRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.location.TripRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Redraws the widgets when what they show changes (PRD F-16), instead of on a timer: entries, vehicles, reminders and
 * settings for Quick add and Status, the recorder for Trip controls. Bursts (an import, a restore) collapse into one
 * redraw. Data only changes inside the app's process, so watching while it runs is enough; widgets also refresh once a
 * day for date-based reminders (appwidget-provider updatePeriodMillis). Started from the Application.
 *
 * Pro widgets (Dashboard 4×2, Consumption; PRD F-16) are deferred with billing (F-19). Each will be a GlanceAppWidget +
 * receiver like [StatusWidget], declared in this module's manifest, gated through EntitlementRepository, and redrawn
 * from the data signal below.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vehicles: VehicleRepository,
    private val userData: UserDataRepository,
    private val entries: EntryRepository,
    private val reminders: ReminderRepository,
    private val categories: CategoryRepository,
    private val trips: TripRepository,
    private val recorder: TripRecorder,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            // Each source's first value lands at once, so the widgets also redraw when the process starts (after a
            // permission change, say).
            merge(
                vehicles.observeVehicles(),
                userData.userData,
                entries.observeAllRefuels(),
                entries.observeAllExpenses(),
                reminders.observeReminders(),
                categories.observeCategories(),
                trips.observeTrips(),
            ).debounce(DATA_DEBOUNCE_MS).collectLatest {
                update(StatusWidget())
                update(QuickAddWidget())
                update(TripWidget())
            }
        }
        scope.launch {
            recorder.state.map(::tripKey).distinctUntilChanged().debounce(TRIP_DEBOUNCE_MS).collectLatest {
                update(TripWidget())
            }
        }
    }

    /** Skips the work when no widget of this kind is on the home screen. */
    private suspend fun update(widget: GlanceAppWidget) {
        if (GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).isNotEmpty()) widget.updateAll(context)
    }

    /** What the trip widget draws: recording or not, paused, and the distance to 0.1 km. */
    private fun tripKey(state: RecordingState): Triple<String?, Boolean, Long> = when (state) {
        is RecordingState.Active ->
            Triple(state.tripId, state.paused, (state.summary.distanceKm * TENTHS_PER_KM).roundToLong())

        RecordingState.Idle -> Triple(null, false, 0)
    }

    private companion object {
        const val DATA_DEBOUNCE_MS = 1_000L
        const val TRIP_DEBOUNCE_MS = 500L
        const val TENTHS_PER_KM = 10.0
    }
}

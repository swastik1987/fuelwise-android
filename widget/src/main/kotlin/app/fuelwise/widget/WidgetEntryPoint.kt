package app.fuelwise.widget

import android.content.Context
import app.fuelwise.core.location.TripRecorder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Glance builds widgets itself, outside Hilt's reach; they fetch what they need here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun data(): WidgetDataSource

    fun recorder(): TripRecorder
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

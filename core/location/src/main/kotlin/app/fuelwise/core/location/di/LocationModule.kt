package app.fuelwise.core.location.di

import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.location.FusedTripLocationSource
import app.fuelwise.core.location.NotificationTripPrompts
import app.fuelwise.core.location.RecorderScope
import app.fuelwise.core.location.RecordingHost
import app.fuelwise.core.location.ServiceRecordingHost
import app.fuelwise.core.location.TripLocationSource
import app.fuelwise.core.location.TripPrompts
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal interface LocationModule {
    @Binds
    fun locationSource(impl: FusedTripLocationSource): TripLocationSource

    @Binds
    fun recordingHost(impl: ServiceRecordingHost): RecordingHost

    @Binds
    fun tripPrompts(impl: NotificationTripPrompts): TripPrompts

    companion object {
        @Provides
        @Singleton
        @RecorderScope
        fun recorderScope(@Dispatcher(FwDispatcher.Default) dispatcher: CoroutineDispatcher): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatcher)
    }
}

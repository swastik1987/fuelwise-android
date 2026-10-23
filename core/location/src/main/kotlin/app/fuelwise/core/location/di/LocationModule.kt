package app.fuelwise.core.location.di

import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.location.ActivityTransitionDriveDetection
import app.fuelwise.core.location.AutoTripStarter
import app.fuelwise.core.location.AutoTrips
import app.fuelwise.core.location.CarLinker
import app.fuelwise.core.location.CarPresence
import app.fuelwise.core.location.CompanionCarPresence
import app.fuelwise.core.location.DriveDetection
import app.fuelwise.core.location.FillUpCheck
import app.fuelwise.core.location.FillUpDetector
import app.fuelwise.core.location.FillUpNotifier
import app.fuelwise.core.location.FusedSingleFix
import app.fuelwise.core.location.FusedTripLocationSource
import app.fuelwise.core.location.NotificationFillUpNotifier
import app.fuelwise.core.location.NotificationTripPrompts
import app.fuelwise.core.location.RecorderScope
import app.fuelwise.core.location.RecordingHost
import app.fuelwise.core.location.ServiceAutoTripStarter
import app.fuelwise.core.location.ServiceRecordingHost
import app.fuelwise.core.location.SingleFix
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

    @Binds
    fun driveDetection(impl: ActivityTransitionDriveDetection): DriveDetection

    @Binds
    fun autoTripStarter(impl: ServiceAutoTripStarter): AutoTripStarter

    @Binds
    fun carPresence(impl: CompanionCarPresence): CarPresence

    @Binds
    fun carLinker(impl: AutoTrips): CarLinker

    @Binds
    fun singleFix(impl: FusedSingleFix): SingleFix

    @Binds
    fun fillUpCheck(impl: FillUpDetector): FillUpCheck

    @Binds
    fun fillUpNotifier(impl: NotificationFillUpNotifier): FillUpNotifier

    companion object {
        @Provides
        @Singleton
        @RecorderScope
        fun recorderScope(@Dispatcher(FwDispatcher.Default) dispatcher: CoroutineDispatcher): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatcher)
    }
}

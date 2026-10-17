package app.fuelwise.di

import app.fuelwise.BuildConfig
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.sync.drive.WEB_CLIENT_ID_NAME
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** All time comes from here so tests can fix it (CLAUDE.md: injected Clock, UTC). */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    // The one place dispatchers are named; everything else injects them.
    @Provides
    @Dispatcher(FwDispatcher.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(FwDispatcher.Default)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** From local.properties via the Secrets Gradle Plugin; "not-configured" until setup step L1. */
    @Provides
    @Named(WEB_CLIENT_ID_NAME)
    fun googleWebClientId(): String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    /** Lives as long as the process: background observers such as the backup scheduler. */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@Dispatcher(FwDispatcher.Default) dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}

@Module
@InstallIn(SingletonComponent::class)
interface IdModule {
    @Binds
    @Singleton
    fun idGenerator(impl: UuidV7Generator): IdGenerator
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

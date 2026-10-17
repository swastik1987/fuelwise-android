package app.fuelwise.di

import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.domain.id.UuidV7Generator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

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
}

@Module
@InstallIn(SingletonComponent::class)
interface IdModule {
    @Binds
    @Singleton
    fun idGenerator(impl: UuidV7Generator): IdGenerator
}

package app.fuelwise.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import app.fuelwise.core.datastore.UserPreferences
import app.fuelwise.core.datastore.UserPreferencesSerializer
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun userPreferencesStore(
        @ApplicationContext context: Context,
        serializer: UserPreferencesSerializer,
        @Dispatcher(FwDispatcher.IO) io: CoroutineDispatcher,
    ): DataStore<UserPreferences> = DataStoreFactory.create(
        serializer = serializer,
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.dataStoreFile("user_preferences.pb") },
    )
}

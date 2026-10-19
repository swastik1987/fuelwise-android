package app.fuelwise.feature.trips

import android.content.Context
import android.net.Uri
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Writes an export to a place the user picked with the system file picker (SAF): no storage permission. */
interface TripFileWriter {
    /** False when the file couldn't be written (the provider went away, storage full). */
    suspend fun write(uri: Uri, text: String): Boolean
}

internal class ContentResolverTripFileWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : TripFileWriter {
    override suspend fun write(uri: Uri, text: String): Boolean = withContext(io) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TripsModule {
    @Binds
    fun fileWriter(impl: ContentResolverTripFileWriter): TripFileWriter
}

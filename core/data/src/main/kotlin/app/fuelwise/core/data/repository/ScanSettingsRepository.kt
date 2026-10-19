package app.fuelwise.core.data.repository

import app.fuelwise.core.datastore.PreferencesDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Scanning preferences (PRD F-15). Kept apart from [UserDataRepository] so settings screens can grow separately. */
interface ScanSettingsRepository {
    /** Whether a pump or receipt scan's photo is attached to the saved entry ("optional, setting"). On by default. */
    val attachPhoto: Flow<Boolean>

    suspend fun setAttachPhoto(attach: Boolean)
}

internal class DataStoreScanSettingsRepository @Inject constructor(private val source: PreferencesDataSource) :
    ScanSettingsRepository {
    override val attachPhoto: Flow<Boolean> = source.preferences.map { !it.scanPhotoOptOut }

    override suspend fun setAttachPhoto(attach: Boolean) = source.update { scanPhotoOptOut = !attach }
}

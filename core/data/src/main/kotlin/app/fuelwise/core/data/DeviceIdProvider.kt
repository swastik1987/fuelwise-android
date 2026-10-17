package app.fuelwise.core.data

import app.fuelwise.core.datastore.PreferencesDataSource
import javax.inject.Inject

/** This install's id, stamped on every write (CLAUDE.md rule 3). */
fun interface DeviceIdProvider {
    suspend fun deviceId(): String
}

internal class DataStoreDeviceIdProvider @Inject constructor(private val source: PreferencesDataSource) :
    DeviceIdProvider {
    override suspend fun deviceId(): String = source.deviceId()
}

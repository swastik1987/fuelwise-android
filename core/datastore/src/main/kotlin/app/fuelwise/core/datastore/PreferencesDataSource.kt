package app.fuelwise.core.datastore

import androidx.datastore.core.DataStore
import app.fuelwise.core.domain.id.IdGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Thin, typed access to the preferences DataStore. Repositories in core:data build on this. */
class PreferencesDataSource @Inject constructor(
    private val store: DataStore<UserPreferences>,
    private val ids: IdGenerator,
) {
    val preferences: Flow<UserPreferences> = store.data

    /** This install's device id, created on first use. Safe to call concurrently. */
    suspend fun deviceId(): String {
        store.data.first().deviceId.takeIf { it.isNotEmpty() }?.let { return it }
        return store.updateData { prefs ->
            if (prefs.deviceId.isNotEmpty()) prefs else prefs.copy { deviceId = ids.newId() }
        }.deviceId
    }

    suspend fun update(transform: UserPreferencesKt.Dsl.() -> Unit) {
        store.updateData { it.copy(transform) }
    }
}

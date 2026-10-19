package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.ScanSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ScanSettingsRepository]; photos are attached by default, as in the app. */
class FakeScanSettingsRepository(attach: Boolean = true) : ScanSettingsRepository {
    override val attachPhoto = MutableStateFlow(attach)

    override suspend fun setAttachPhoto(attach: Boolean) {
        attachPhoto.value = attach
    }
}

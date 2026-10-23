package app.fuelwise.feature.garage.photo

import androidx.compose.ui.graphics.ImageBitmap
import app.fuelwise.core.data.repository.VehiclePhotoRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.ui.photo.PhotoImages
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Vehicle photos decoded for display (PRD F-01.1, design.md screens 21 and 22), by vehicle id. */
class VehiclePhotoLoader @Inject constructor(
    private val photos: VehiclePhotoRepository,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) {
    fun observe(maxPx: Int): Flow<Map<String, ImageBitmap>> = photos.observePhotos().map { byVehicle ->
        withContext(io) {
            byVehicle.mapNotNull { (vehicleId, photo) ->
                runCatching { PhotoImages.decode(photos.read(photo), maxPx) }.getOrNull()?.let { vehicleId to it }
            }.toMap()
        }
    }
}

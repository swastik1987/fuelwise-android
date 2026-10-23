package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.VehiclePhotoRepository
import app.fuelwise.core.model.Attachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeVehiclePhotoRepository : VehiclePhotoRepository {
    /** The saved JPEG of each vehicle. */
    val jpegs = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    override fun observePhotos(): Flow<Map<String, Attachment>> = jpegs.map { all ->
        all.mapValues { (vehicleId, bytes) ->
            Attachment(
                "photo-$vehicleId",
                vehicleId,
                Attachment.OwnerType.Vehicle,
                vehicleId,
                "image/jpeg",
                bytes.size.toLong(),
                "",
            )
        }
    }

    override suspend fun read(photo: Attachment): ByteArray = jpegs.value.getValue(photo.ownerId)

    override suspend fun setPhoto(vehicleId: String, jpeg: ByteArray) = jpegs.update { it + (vehicleId to jpeg) }

    override suspend fun removePhoto(vehicleId: String) = jpegs.update { it - vehicleId }
}

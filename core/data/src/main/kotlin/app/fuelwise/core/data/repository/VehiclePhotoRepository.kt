package app.fuelwise.core.data.repository

import app.fuelwise.core.data.mapper.toModel
import app.fuelwise.core.database.dao.DocumentDao
import app.fuelwise.core.model.Attachment
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One photo per vehicle (PRD F-01.1), kept as an encrypted attachment like document scans, so it's backed up with
 * them. Callers hand over a JPEG already shrunk for the screen.
 */
interface VehiclePhotoRepository {
    /** The current photo of every vehicle that has one, by vehicle id. */
    fun observePhotos(): Flow<Map<String, Attachment>>

    suspend fun read(photo: Attachment): ByteArray

    /** Replaces the vehicle's photo. */
    suspend fun setPhoto(vehicleId: String, jpeg: ByteArray)

    suspend fun removePhoto(vehicleId: String)
}

internal class OfflineVehiclePhotoRepository @Inject constructor(
    private val dao: DocumentDao,
    private val attachments: DocumentRepository,
) : VehiclePhotoRepository {
    override fun observePhotos(): Flow<Map<String, Attachment>> =
        dao.observeAttachmentsOfType(Attachment.OwnerType.Vehicle.name).map { rows ->
            // Oldest first, so the newest wins if a replace was interrupted.
            rows.map { it.toModel() }.associateBy { it.ownerId }
        }

    override suspend fun read(photo: Attachment): ByteArray = attachments.readAttachment(photo)

    override suspend fun setPhoto(vehicleId: String, jpeg: ByteArray) {
        val old = current(vehicleId)
        attachments.addAttachment(vehicleId, Attachment.OwnerType.Vehicle, jpeg, MIME_JPEG)
        old.forEach { attachments.removeAttachment(it) }
    }

    override suspend fun removePhoto(vehicleId: String) = current(vehicleId).forEach {
        attachments.removeAttachment(it)
    }

    private suspend fun current(vehicleId: String): List<Attachment> =
        dao.observeAttachments(vehicleId).first().map { it.toModel() }.filter {
            it.ownerType == Attachment.OwnerType.Vehicle
        }

    private companion object {
        const val MIME_JPEG = "image/jpeg"
    }
}

package app.fuelwise.core.data.mapper

import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.database.model.TripEntity
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus

internal fun TripEntity.toModel() = Trip(
    id = id,
    vehicleId = vehicleId,
    startUtcMs = startTime,
    endUtcMs = endTime,
    zoneId = zoneId,
    startOdometerKm = startOdo,
    endOdometerKm = endOdo,
    distanceKm = distanceKm,
    purpose = enumValueOrNull<TripPurpose>(purpose) ?: TripPurpose.Personal,
    customPurpose = customPurpose,
    fromPlace = fromPlace,
    toPlace = toPlace,
    notes = notes,
    estimatedFuelCost = estCostPaise?.let(::Money),
    source = enumValueOrNull<TripSource>(source) ?: TripSource.Manual,
    status = enumValueOrNull<TripStatus>(status) ?: TripStatus.Done,
    recordedSeconds = recordedSeconds,
    idleSeconds = idleSeconds,
    maxSpeedKmh = maxSpeedKmh,
)

internal fun Trip.toEntity(sync: SyncMeta, polylineAttachmentId: String? = null) = TripEntity(
    id = id,
    vehicleId = vehicleId,
    startTime = startUtcMs,
    endTime = endUtcMs,
    zoneId = zoneId,
    startOdo = startOdometerKm,
    endOdo = endOdometerKm,
    distanceKm = distanceKm,
    purpose = purpose.name,
    customPurpose = customPurpose?.trim()?.ifEmpty { null },
    fromPlace = fromPlace?.trim()?.ifEmpty { null },
    toPlace = toPlace?.trim()?.ifEmpty { null },
    notes = notes?.trim()?.ifEmpty { null },
    polylineAttachmentId = polylineAttachmentId,
    estCostPaise = estimatedFuelCost?.paise,
    source = source.name,
    status = status.name,
    recordedSeconds = recordedSeconds,
    idleSeconds = idleSeconds,
    maxSpeedKmh = maxSpeedKmh,
    sync = sync,
)

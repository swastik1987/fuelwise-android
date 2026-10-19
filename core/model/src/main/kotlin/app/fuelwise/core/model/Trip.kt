package app.fuelwise.core.model

/** Why a trip was made (PRD F-07.1). [Custom] carries the user's own label in [Trip.customPurpose]. */
enum class TripPurpose { Personal, Business, Commute, Custom }

/** How the trip was recorded: typed in (R1), GPS from the app (R1, M8), or automatic (R2, F-21.3). */
enum class TripSource { Manual, Gps, Auto }

enum class TripStatus { Recording, Paused, Done, Interrupted }

/**
 * A trip (PRD F-07). Manual trips need a date and either both odometer readings or a distance.
 * [estimatedFuelCost] is fixed when the trip is saved, from the vehicle's own history; linked tolls and parking are
 * expenses whose `tripId` is this trip, and are added on top when shown.
 */
data class Trip(
    val id: String,
    val vehicleId: String,
    val startUtcMs: Long,
    val zoneId: String,
    val distanceKm: Double,
    val purpose: TripPurpose,
    val endUtcMs: Long? = null,
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
    val customPurpose: String? = null,
    /** Free text the user typed, e.g. "HSR Layout". Never looked up from coordinates (CLAUDE.md rule 5). */
    val fromPlace: String? = null,
    val toPlace: String? = null,
    val notes: String? = null,
    val estimatedFuelCost: Money? = null,
    val source: TripSource = TripSource.Manual,
    val status: TripStatus = TripStatus.Done,
    /** GPS trips: time recorded, pauses left out (PRD F-07.2). Null for manual trips. */
    val recordedSeconds: Long? = null,
    /** GPS trips: time spent standing still while recording. */
    val idleSeconds: Long? = null,
    val maxSpeedKmh: Double? = null,
)

/**
 * One GPS fix on a recorded trip. [segment] goes up after each pause, so the gap is never counted as driven.
 * Raw points are encrypted at rest and deleted once the trip is finished (CLAUDE.md rule 5).
 */
data class TrackPoint(
    val atUtcMs: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val speedMps: Float?,
    val segment: Int,
)

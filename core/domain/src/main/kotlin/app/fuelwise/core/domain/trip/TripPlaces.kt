package app.fuelwise.core.domain.trip

import kotlin.math.roundToLong

// Trip start and end names (owner decision 12 Nov 2026, user feedback): "Koramangala → Whitefield" instead of the
// purpose. Private places name their own ends; everything else is looked up with Android Geocoder, the one
// documented exception to "no coordinates to a lookup service" (CLAUDE.md rule 5). Pure rules here; the lookup is
// in core:location.

/** The parts of a reverse-geocoded address the label is chosen from; any may be missing. */
data class AddressParts(
    val subLocality: String? = null,
    val locality: String? = null,
    val subAdminArea: String? = null,
    val featureName: String? = null,
)

/**
 * The short name for a trip end: the neighbourhood ("Indiranagar"), else the town or city, else the district. A
 * feature name only when it's a real name, not a house number or plus code.
 */
fun placeLabel(parts: AddressParts): String? = listOf(parts.subLocality, parts.locality, parts.subAdminArea)
    .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
    ?: parts.featureName?.trim()?.takeIf { name -> name.length >= MIN_FEATURE_CHARS && name.none { it.isDigit() } }

/**
 * The private place a trip end belongs to. Saved routes are already trimmed [PRIVATE_RADIUS_M] around private places,
 * so a stored end sits just outside one; [radiusM] is twice that, so such an end is still named after the place and
 * never looked up.
 */
fun privatePlaceFor(
    lat: Double,
    lng: Double,
    places: List<PrivatePlace>,
    radiusM: Double = PRIVATE_RADIUS_M * 2,
): PrivatePlace? = places
    .map { it to distanceM(it.lat, it.lng, lat, lng) }
    .filter { it.second <= radiusM }
    .minByOrNull { it.second }
    ?.first

/** Rounds a coordinate to 3 decimals (about 110 m) before it's looked up: a neighbourhood name needs no more. */
fun roundForLookup(degrees: Double): Double = (degrees * LOOKUP_SCALE).roundToLong() / LOOKUP_SCALE

private const val MIN_FEATURE_CHARS = 3
private const val LOOKUP_SCALE = 1_000.0

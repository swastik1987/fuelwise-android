package app.fuelwise.feature.trips

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Duration
import java.time.LocalDate

internal object TripLabels {
    @StringRes
    fun purpose(purpose: TripPurpose): Int = when (purpose) {
        TripPurpose.Personal -> R.string.trips_purpose_personal
        TripPurpose.Business -> R.string.trips_purpose_business
        TripPurpose.Commute -> R.string.trips_purpose_commute
        TripPurpose.Custom -> R.string.trips_purpose_other
    }

    @DrawableRes
    fun purposeIcon(purpose: TripPurpose): Int = when (purpose) {
        TripPurpose.Personal -> FwIcons.Person
        TripPurpose.Business -> FwIcons.Business
        TripPurpose.Commute -> FwIcons.Commute
        TripPurpose.Custom -> FwIcons.Route
    }
}

/** The user's own label for a custom purpose, else the preset name. */
@Composable
internal fun purposeName(trip: Trip): String = trip.customPurpose?.takeIf { trip.purpose == TripPurpose.Custom }
    ?: stringResource(TripLabels.purpose(trip.purpose))

/** "HSR Layout → Whitefield", one place if only one was typed, else the purpose. */
@Composable
internal fun tripTitle(trip: Trip): String {
    val from = trip.fromPlace
    val to = trip.toPlace
    return when {
        from != null && to != null -> stringResource(R.string.trips_route, from, to)
        else -> from ?: to ?: purposeName(trip)
    }
}

/** "52 min", "2 h 48 min". GPS trips count recorded time (pauses left out); null without an end time. */
@Composable
internal fun tripDuration(trip: Trip): String? {
    val minutes = trip.recordedSeconds?.let { it / SECONDS_PER_MINUTE }
        ?: trip.endUtcMs?.let { Duration.ofMillis(it - trip.startUtcMs).toMinutes().coerceAtLeast(0) }
        ?: return null
    return durationText(minutes)
}

@Composable
internal fun durationText(minutes: Long): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0L -> stringResource(R.string.trips_minutes, minutes)
        rest == 0L -> stringResource(R.string.trips_hours, hours)
        else -> stringResource(R.string.trips_hours_minutes, hours, rest)
    }
}

/** Distance with one decimal under 100 km, whole km above (design.md screen 17). */
internal fun formatKm(km: Double): String =
    if (km < WHOLE_KM_FROM) IndianNumberFormat.decimal(km, 1) else IndianNumberFormat.decimal(km, 0)

private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_MINUTE = 60L
private const val WHOLE_KM_FROM = 100.0

/** "fuelwise-trip-2026-10-12.gpx": the name the file picker suggests for one trip. */
internal fun gpxFileName(date: LocalDate) = "fuelwise-trip-$date.gpx"

/** "fuelwise-trips-2026-10-19.gpx" for a range export, dated the day it was made. */
internal fun gpxRangeFileName(today: LocalDate) = "fuelwise-trips-$today.gpx"

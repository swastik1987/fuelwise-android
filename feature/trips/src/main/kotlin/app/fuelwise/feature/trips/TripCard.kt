package app.fuelwise.feature.trips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.component.FwEntryTile
import app.fuelwise.core.designsystem.component.FwRouteCard
import app.fuelwise.core.designsystem.component.FwRouteThumbnail
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.domain.trip.RouteSketch
import app.fuelwise.core.domain.trip.averageSpeedKmh
import app.fuelwise.core.domain.trip.costPerKm
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.ui.entry.cardDate
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Instant
import java.time.ZoneId

/** What a Trips card needs: the row, how to draw its route, and where a tap goes. */
internal class TripCardInput(
    val row: TripRow,
    val zone: ZoneId,
    /** null while not drawn yet, or when the trip has no route. */
    val sketch: RouteSketch?,
    val onOpen: (String) -> Unit,
    val onSketch: (String) -> Unit,
)

/**
 * A trip as a route-first card (owner choice 24 Nov 2026, option B; design "Logbook entry card" shell): the date,
 * purpose and time over the distance; a route thumbnail drawn on the phone from the saved route; "From → To" above a
 * time tile (with average speed) and a cost tile (with ₹/km). Manual trips have no route and show their icon.
 */
@Composable
internal fun TripCard(input: TripCardInput) {
    val trip = input.row.trip
    val recorded = trip.source != TripSource.Manual
    if (recorded) LaunchedEffect(trip.id) { input.onSketch(trip.id) }

    val date = cardDate(trip.startUtcMs, input.zone)
    val time = Instant.ofEpochMilli(trip.startUtcMs).atZone(input.zone).toLocalTime().format(TIME_FORMAT)
    val subtitle = listOfNotNull(
        purposeName(trip),
        time,
        input.row.vehicleName,
        // Recorded on its own (PRD F-21.3): "Auto" tag, design.md screen 17.
        if (trip.source == TripSource.Auto) stringResource(R.string.trips_auto_tag) else null,
    ).joinToString(" · ")
    val distance = stringResource(R.string.trips_km, formatKm(trip.distanceKm))
    val title = tripTitle(trip)

    val duration = tripDuration(trip)
    val timeTile = FwEntryTile(
        value = duration ?: "—",
        caption = if (duration == null) {
            stringResource(R.string.trips_card_no_time)
        } else {
            averageSpeedKmh(trip)?.let { stringResource(R.string.trips_card_speed, IndianNumberFormat.decimal(it, 0)) }
        },
    )
    val cost = input.row.cost
    val costTile = FwEntryTile(
        value = cost?.let { IndianNumberFormat.rupeesWhole(it) } ?: "—",
        caption = costPerKm(cost, trip.distanceKm)?.let {
            stringResource(R.string.trips_card_per_km, IndianNumberFormat.decimal(it.paise / PAISE, 1))
        } ?: stringResource(R.string.trips_card_no_estimate),
    )
    val tiles = listOf(timeTile, costTile)

    FwRouteCard(
        date = date,
        subtitle = subtitle,
        amount = distance,
        title = title,
        tiles = tiles,
        contentDescription = listOf(date, subtitle, distance, title)
            .plus(tiles.map { listOfNotNull(it.value, it.caption).joinToString(", ") })
            .joinToString(", "),
        thumbnail = {
            FwRouteThumbnail(
                xs = input.sketch?.xs,
                ys = input.sketch?.ys,
                fallbackIcon = if (recorded) FwIcons.Route else FwIcons.ManualTrip,
            )
        },
        onClick = { input.onOpen(trip.id) },
    )
}

private val TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ENGLISH)
private const val PAISE = 100.0

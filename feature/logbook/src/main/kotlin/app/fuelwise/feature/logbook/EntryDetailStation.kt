package app.fuelwise.feature.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwOptionRow
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSheet
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.station.StationOption
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.ui.map.FwPlaceMap
import kotlin.math.roundToInt

/** Callbacks for the station card and its "Change station" sheet; defaults make screenshot tests easy. */
internal data class StationActions(
    val onEdit: () -> Unit = {},
    val onPick: (StationOption) -> Unit = {},
    val onType: (String) -> Unit = {},
    val onRemoveLocation: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * Where the fill-up happened (PRD F-02.1; design option "text row + mini map"): the station's name with an edit
 * button, and a small still map with a pin when a position is saved.
 */
@Composable
internal fun StationCard(refuel: Refuel, onEdit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FwOverline(stringResource(R.string.detail_station))
        FwCard(contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        refuel.stationName ?: stringResource(R.string.detail_station_none),
                        style = FwTheme.type.label,
                        color = if (refuel.stationName == null) FwTheme.colors.ink2 else FwTheme.colors.ink,
                    )
                    if (refuel.lat == null) {
                        Text(
                            stringResource(R.string.detail_station_no_location),
                            style = FwTheme.type.bodyS,
                            color = FwTheme.colors.ink2,
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        painterResource(FwIcons.Edit),
                        contentDescription = stringResource(R.string.detail_station_change),
                        tint = FwTheme.colors.ink2,
                    )
                }
            }
            val lat = refuel.lat
            val lng = refuel.lng
            if (lat != null && lng != null) {
                FwPlaceMap(
                    lat = lat,
                    lng = lng,
                    contentDescription = stringResource(
                        R.string.detail_station_map_cd,
                        refuel.stationName ?: stringResource(R.string.detail_station_none),
                    ),
                    modifier = Modifier
                        .padding(end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth()
                        .height(MAP_HEIGHT.dp)
                        .clip(FwTheme.radius.tile),
                )
            }
        }
    }
}

@Composable
internal fun StationEditSheet(edit: StationEdit, actions: StationActions) {
    FwSheet(stringResource(R.string.detail_station_change), actions.onDismiss) { StationEditBody(edit, actions) }
}

/**
 * Nearby stations around the saved position, a search box that also takes a new name, and "Remove location". The
 * user's choice is saved straight away and teaches the next detection at this spot (PRD F-21.1).
 */
@Composable
internal fun StationEditBody(edit: StationEdit, actions: StationActions, initialQuery: String = "") {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val typed = query.trim()
    val shown = edit.options.filter { typed.isEmpty() || it.label.contains(typed, ignoreCase = true) }
    FwTextField(
        value = query,
        onValueChange = { query = it.take(MAX_NAME) },
        label = stringResource(R.string.detail_station_search),
    )
    when {
        !edit.hasLocation -> Hint(stringResource(R.string.detail_station_no_location_hint))
        edit.loading -> Hint(stringResource(R.string.detail_station_finding))
        edit.options.isEmpty() -> Hint(stringResource(R.string.detail_station_nothing_near))
    }
    shown.forEach { option ->
        FwOptionRow(
            title = option.label,
            selected = option.label.equals(edit.current, ignoreCase = true),
            onClick = { actions.onPick(option) },
            subtitle = if (option.usedBefore) stringResource(R.string.detail_station_used_before) else null,
            trailing = stringResource(R.string.detail_station_metres, option.distanceM.roundToInt()),
        )
    }
    if (typed.isNotEmpty() && shown.none { it.label.equals(typed, ignoreCase = true) }) {
        FwOptionRow(
            title = stringResource(R.string.detail_station_use_name, typed),
            selected = false,
            onClick = { actions.onType(typed) },
        )
    }
    if (edit.hasLocation) {
        FwTextButton(stringResource(R.string.detail_station_remove_location), actions.onRemoveLocation)
    }
    if (edit.options.isNotEmpty()) {
        // ODbL attribution wherever the station list is shown (PRD F-21.2).
        Text(stringResource(R.string.detail_station_osm), style = FwTheme.type.caption, color = FwTheme.colors.ink3)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
}

private const val MAP_HEIGHT = 160
private const val MAX_NAME = 60

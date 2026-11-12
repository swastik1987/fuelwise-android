package app.fuelwise.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.ui.map.FwHeatMap
import app.fuelwise.core.ui.map.MapPin
import app.fuelwise.core.ui.vehicle.VehicleLabels

@Composable
internal fun TripsMapScreen(onBack: () -> Unit, viewModel: TripsMapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TripsMapContent(state, onBack, viewModel::selectPeriod)
}

/**
 * The consolidated trips map (PRD F-07.7, free in R1 since 12 Nov 2026): routes as a heatmap, places visited often
 * and fill-ups with a place, for the last 3 months, 12 months or all time. A legend with counts doubles as the
 * text alternative to the map.
 */
@Composable
internal fun TripsMapContent(state: TripsMapUiState, onBack: () -> Unit, onPeriod: (MapPeriod) -> Unit) {
    val placeColor = FwTheme.colors.ink
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.trips_map_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.trips_back),
        )
        FwSegmentedControl(
            options = MapPeriod.entries.map { stringResource(periodLabel(it)) },
            selectedIndex = state.period.ordinal,
            onSelect = { onPeriod(MapPeriod.entries[it]) },
            modifier = Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = FwTheme.spacing.gutter)) {
            if (state.loading) return@Box
            val pins = state.places.mapIndexed { i, p -> MapPin("place-$i", p.lat, p.lng, placeColor) } +
                state.fillUps.map { f ->
                    MapPin(f.id, f.lat, f.lng, f.energy?.let { VehicleLabels.energyColor(it) } ?: placeColor)
                }
            FwHeatMap(
                heat = state.heat,
                pins = pins,
                contentDescription = pluralStringResource(R.plurals.trips_map_desc, state.routes, state.routes),
                modifier = Modifier.fillMaxSize().clip(FwTheme.radius.card),
            )
            if (state.empty) {
                FwCard(Modifier.align(Alignment.Center).padding(FwTheme.spacing.l)) {
                    Text(
                        stringResource(R.string.trips_map_empty),
                        style = FwTheme.type.body,
                        color = FwTheme.colors.ink,
                    )
                    Text(
                        stringResource(R.string.trips_map_empty_body),
                        style = FwTheme.type.bodyS,
                        color = FwTheme.colors.ink2,
                        modifier = Modifier.padding(top = FwTheme.spacing.xxs),
                    )
                }
            }
        }
        if (!state.loading) Legend(state, placeColor)
    }
}

@Composable
private fun Legend(state: TripsMapUiState, placeColor: Color) {
    Column(
        Modifier.navigationBarsPadding().padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
    ) {
        LegendRow(FwTheme.colors.accent, pluralStringResource(R.plurals.trips_map_routes, state.routes, state.routes))
        LegendRow(
            placeColor,
            pluralStringResource(R.plurals.trips_map_places, state.places.size, state.places.size),
        )
        // Fill-up dots take their energy colour; the legend shows the first one's, petrol when there are none.
        LegendRow(
            VehicleLabels.energyColor(state.fillUps.firstNotNullOfOrNull { it.energy } ?: EnergyType.PETROL),
            pluralStringResource(R.plurals.trips_map_fill_ups, state.fillUps.size, state.fillUps.size),
        )
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        Box(Modifier.size(LEGEND_DOT_DP.dp).background(color, CircleShape))
        Text(label, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
    }
}

private fun periodLabel(p: MapPeriod): Int = when (p) {
    MapPeriod.ThreeMonths -> R.string.trips_map_3_months
    MapPeriod.Year -> R.string.trips_map_12_months
    MapPeriod.All -> R.string.trips_map_all
}

private const val LEGEND_DOT_DP = 10

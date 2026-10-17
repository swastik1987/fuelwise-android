package app.fuelwise.feature.garage.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwLargeTitle
import app.fuelwise.core.designsystem.component.FwSectionHeader
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleAvatar
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.core.ui.vehicle.tankLabel
import app.fuelwise.feature.garage.R
import kotlinx.coroutines.withTimeoutOrNull

/** Garage (design.md screen 21): vehicle cards, then archived vehicles. Utility rows join as their features land. */
@Composable
internal fun GarageScreen(
    onOpenVehicle: (String) -> Unit,
    onAddVehicle: () -> Unit,
    viewModel: GarageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GarageEvent.VehicleDeleted -> {
                    // PRD F-01 AC: undo is offered for 10 seconds.
                    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                        snackbar.showSnackbar(
                            message = resources.getString(R.string.garage_deleted, event.name),
                            actionLabel = resources.getString(R.string.garage_undo),
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == null) snackbar.currentSnackbarData?.dismiss()
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.token)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        GarageContent(state, onOpenVehicle, onAddVehicle)
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun GarageContent(state: GarageUiState, onOpenVehicle: (String) -> Unit, onAddVehicle: () -> Unit) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwLargeTitle(stringResource(R.string.garage_title))
        when (state) {
            GarageUiState.Loading -> Unit

            is GarageUiState.Ready -> if (state.active.isEmpty() && state.archived.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FwEmptyState(
                        icon = FwIcons.Garage,
                        headline = stringResource(R.string.garage_empty_title),
                        body = stringResource(R.string.garage_empty_body),
                        actionLabel = stringResource(R.string.garage_empty_action),
                        onAction = onAddVehicle,
                    )
                }
            } else {
                GarageList(state, onOpenVehicle, onAddVehicle)
            }
        }
    }
}

@Composable
private fun GarageList(state: GarageUiState.Ready, onOpenVehicle: (String) -> Unit, onAddVehicle: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = FwTheme.spacing.xxl)) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
            ) {
                items(state.active, key = { it.id }) { vehicle ->
                    VehicleCard(vehicle, onClick = { onOpenVehicle(vehicle.id) })
                }
            }
        }
        item {
            AddVehicleButton(
                onAddVehicle,
                Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
            )
        }
        if (state.archived.isNotEmpty()) {
            item {
                FwSectionHeader(
                    stringResource(R.string.garage_archived, state.archived.size),
                    Modifier.padding(
                        start = FwTheme.spacing.gutter,
                        end = FwTheme.spacing.gutter,
                        top = FwTheme.spacing.xl,
                    ),
                )
            }
            items(state.archived, key = { it.id }) { vehicle ->
                ArchivedRow(vehicle, onClick = { onOpenVehicle(vehicle.id) })
            }
        }
    }
}

@Composable
private fun VehicleCard(vehicle: Vehicle, onClick: () -> Unit) {
    val colors = FwTheme.colors
    FwCard(modifier = Modifier.width(236.dp), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        // Photo area until vehicle photos are supported: the type glyph on surface-2.
        Box(Modifier.fillMaxWidth().height(112.dp).background(colors.surface2), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(VehicleLabels.typeIcon(vehicle.type)),
                null,
                tint = colors.ink3,
                modifier = Modifier.size(40.dp),
            )
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    vehicle.name,
                    style = FwTheme.type.h3,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.garage_km, IndianNumberFormat.decimal(vehicle.startOdometerKm, 0)),
                    style = FwTheme.type.bodyS,
                    color = colors.ink2,
                )
            }
            vehicle.registrationNumber?.let { Text(it, style = FwTheme.type.monoS, color = colors.ink2) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                vehicle.tanks.forEach { tank -> FuelTag(tankLabel(tank), VehicleLabels.energyColor(tank.energyType)) }
            }
        }
    }
}

@Composable
private fun FuelTag(label: String, dot: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.height(22.dp).background(FwTheme.colors.surface2, FwTheme.radius.pill).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(label, style = FwTheme.type.caption, color = FwTheme.colors.ink)
    }
}

@Composable
private fun AddVehicleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(
            min = 56.dp,
        ).border(1.5.dp, FwTheme.colors.ink3, FwTheme.radius.button),
        shape = FwTheme.radius.button,
        color = FwTheme.colors.bg,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(FwIcons.Add), contentDescription = null, tint = FwTheme.colors.ink)
            Text(
                stringResource(R.string.garage_empty_action),
                style = FwTheme.type.label,
                color = FwTheme.colors.ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ArchivedRow(vehicle: Vehicle, onClick: () -> Unit) {
    Surface(onClick = onClick, color = FwTheme.colors.bg) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = FwTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
        ) {
            VehicleAvatar(vehicle, size = 40)
            Column(Modifier.weight(1f)) {
                Text(vehicle.name, style = FwTheme.type.body, color = FwTheme.colors.ink2)
                vehicle.registrationNumber?.let { Text(it, style = FwTheme.type.monoS, color = FwTheme.colors.ink3) }
            }
            Icon(painterResource(FwIcons.ChevronRight), contentDescription = null, tint = FwTheme.colors.ink3)
        }
    }
}

private const val UNDO_WINDOW_MS = 10_000L

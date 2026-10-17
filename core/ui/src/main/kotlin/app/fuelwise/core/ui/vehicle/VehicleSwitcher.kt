package app.fuelwise.core.ui.vehicle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.R

/** Pill at the top-left of Home, Logbook and Insights: avatar, vehicle name, chevron (docs/design.md §5). */
@Composable
fun VehicleSwitcherPill(vehicle: Vehicle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FwTheme.colors
    val description = stringResource(R.string.vehicle_switcher_cd, vehicle.name)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        shape = FwTheme.radius.pill,
        color = colors.surface,
        border = colors.cardBorder.takeIf { it != Color.Transparent }?.let { BorderStroke(1.dp, it) },
    ) {
        Row(
            Modifier.height(40.dp).padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VehicleAvatar(vehicle, size = 24)
            Text(
                vehicle.name,
                style = FwTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Icon(
                painterResource(FwIcons.ExpandMore),
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Sheet listing vehicles to switch between. [showAllVehicles] adds the "All vehicles" aggregate at the top
 * (stats screens only); selecting it calls [onSelect] with null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSwitcherSheet(
    vehicles: List<Vehicle>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onAddVehicle: () -> Unit,
    onDismiss: () -> Unit,
    showAllVehicles: Boolean = false,
) {
    val colors = FwTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = FwTheme.spacing.m)) {
            Text(
                stringResource(R.string.vehicle_switcher_title),
                style = FwTheme.type.h3,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
            )
            if (showAllVehicles) {
                SwitcherRow(stringResource(R.string.vehicle_switcher_all), null, selectedId == null) { onSelect(null) }
            }
            vehicles.forEach { vehicle ->
                SwitcherRow(vehicle.name, vehicle, vehicle.id == selectedId) { onSelect(vehicle.id) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddVehicle, role = Role.Button)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = FwTheme.spacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(painterResource(FwIcons.Add), contentDescription = null, tint = colors.ink)
                Text(stringResource(R.string.vehicle_add), style = FwTheme.type.body, color = colors.ink)
            }
        }
    }
}

@Composable
private fun SwitcherRow(label: String, vehicle: Vehicle?, selected: Boolean, onClick: () -> Unit) {
    val colors = FwTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = FwTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (vehicle != null) {
            VehicleAvatar(vehicle, size = 32)
        } else {
            Box(Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = FwTheme.type.body, color = colors.ink)
            vehicle?.registrationNumber?.let {
                Text(it, style = FwTheme.type.monoS, color = colors.ink2)
            }
        }
        if (selected) Icon(painterResource(FwIcons.Check), contentDescription = null, tint = colors.ink)
    }
}

/** Placeholder avatar until vehicle photos exist: a neutral circle with the vehicle-type glyph. */
@Composable
fun VehicleAvatar(vehicle: Vehicle, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).background(FwTheme.colors.surface2, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(VehicleLabels.typeIcon(vehicle.type)),
            contentDescription = null,
            tint = FwTheme.colors.ink2,
            modifier = Modifier.size((size * AVATAR_ICON_RATIO).dp),
        )
    }
}

private const val AVATAR_ICON_RATIO = 0.62f

/** Switcher pill while the "All vehicles" aggregate is selected (Logbook, Insights). */
@Composable
fun AllVehiclesPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = FwTheme.radius.pill, color = FwTheme.colors.surface) {
        Row(Modifier.height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.vehicle_switcher_all),
                style = FwTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                color = FwTheme.colors.ink,
            )
            Icon(painterResource(FwIcons.ExpandMore), null, tint = FwTheme.colors.ink2)
        }
    }
}

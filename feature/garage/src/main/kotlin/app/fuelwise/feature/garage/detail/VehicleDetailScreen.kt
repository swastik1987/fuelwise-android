package app.fuelwise.feature.garage.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.feature.garage.R

@Composable
internal fun VehicleDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        VehicleDetailUiState.Loading -> Box(Modifier.fillMaxSize().background(FwTheme.colors.bg))

        VehicleDetailUiState.Gone -> LaunchedEffect(Unit) { onBack() }

        is VehicleDetailUiState.Ready -> VehicleDetailContent(
            vehicle = s.vehicle,
            onBack = onBack,
            onEdit = { onEdit(s.vehicle.id) },
            onArchive = viewModel::setArchived,
            onDelete = { onDelete(s.vehicle.id) },
            confirmationMatches = { typed -> viewModel.confirmationMatches(typed, s.vehicle) },
        )
    }
}

/** Vehicle detail (design.md screen 22). Photo, purchase details and car Bluetooth arrive with their features. */
@Composable
internal fun VehicleDetailContent(
    vehicle: Vehicle,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    confirmationMatches: (String) -> Boolean,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = vehicle.name,
            onBack = onBack,
            backContentDescription = stringResource(R.string.editor_back),
            actions = {
                IconButton(onClick = onEdit) {
                    Icon(painterResource(FwIcons.Edit), stringResource(R.string.detail_edit), tint = FwTheme.colors.ink)
                }
            },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = FwTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
        ) {
            Header(vehicle)
            Section(stringResource(R.string.detail_energy)) {
                vehicle.tanks.forEachIndexed { index, tank ->
                    if (index > 0) HorizontalDivider(color = FwTheme.colors.line)
                    Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(VehicleLabels.energyColor(tank.energyType), CircleShape))
                        Text(
                            stringResource(VehicleLabels.energy(tank.energyType)) +
                                if (tank.isPrimary &&
                                    vehicle.isBiFuel
                                ) {
                                    " · " + stringResource(R.string.detail_primary)
                                } else {
                                    ""
                                },
                            style = FwTheme.type.body,
                            color = FwTheme.colors.ink,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        Text(
                            tank.capacity?.let {
                                IndianNumberFormat.decimal(it, if (it % 1.0 == 0.0) 0 else 1) + " " +
                                    stringResource(VehicleLabels.unit(tank.energyType.canonicalUnit))
                            } ?: stringResource(R.string.detail_not_set),
                            style = FwTheme.type.label,
                            color = if (tank.capacity == null) FwTheme.colors.ink3 else FwTheme.colors.ink,
                        )
                    }
                }
            }
            Section(stringResource(R.string.detail_efficiency)) {
                ValueRow(
                    stringResource(R.string.editor_claimed),
                    vehicle.claimedKmPerUnit?.let {
                        IndianNumberFormat.decimal(it, 1) + " " + stringResource(
                            R.string.editor_claimed_unit,
                            stringResource(VehicleLabels.unit(vehicle.primaryTank!!.energyType.canonicalUnit)),
                        )
                    },
                )
            }
            Section(stringResource(R.string.detail_registration)) {
                ValueRow(stringResource(R.string.editor_registration), vehicle.registrationNumber, mono = true)
                HorizontalDivider(color = FwTheme.colors.line)
                ValueRow(
                    stringResource(R.string.editor_emission_norm),
                    vehicle.emissionNorm?.let {
                        stringResource(normName(it))
                    },
                )
            }
            Column(Modifier.padding(horizontal = FwTheme.spacing.gutter)) {
                ActionRow(
                    icon = if (vehicle.isArchived) FwIcons.Unarchive else FwIcons.Archive,
                    title = stringResource(
                        if (vehicle.isArchived) R.string.detail_unarchive else R.string.detail_archive,
                    ),
                    subtitle = stringResource(R.string.detail_archive_hint),
                    tint = FwTheme.colors.ink,
                    onClick = { onArchive(!vehicle.isArchived) },
                )
                ActionRow(
                    icon = FwIcons.Delete,
                    title = stringResource(R.string.detail_delete),
                    subtitle = null,
                    tint = FwTheme.colors.negative,
                    onClick = { confirmingDelete = true },
                )
            }
        }
    }
    if (confirmingDelete) {
        DeleteDialog(
            vehicleName = vehicle.name,
            matches = confirmationMatches,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun Header(vehicle: Vehicle) {
    // Ink block stands in for the header photo until vehicle photos exist.
    Column(
        Modifier
            .padding(horizontal = FwTheme.spacing.gutter)
            .fillMaxWidth()
            .background(FwTheme.colors.ink, FwTheme.radius.card)
            .padding(FwTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(VehicleLabels.typeIcon(vehicle.type)),
            null,
            tint = FwTheme.colors.bg,
            modifier = Modifier.size(32.dp),
        )
        Text(vehicle.name, style = FwTheme.type.h1, color = FwTheme.colors.bg, modifier = Modifier.padding(top = 12.dp))
        Text(
            listOfNotNull(
                vehicle.registrationNumber,
                stringResource(R.string.garage_km, IndianNumberFormat.decimal(vehicle.startOdometerKm, 0)),
            ).joinToString(" · "),
            style = FwTheme.type.monoS,
            color = FwTheme.colors.bg.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = FwTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FwOverline(title)
        FwCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) { content() }
    }
}

@Composable
private fun ValueRow(label: String, value: String?, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FwTheme.type.body, color = FwTheme.colors.ink, modifier = Modifier.weight(1f))
        Text(
            value ?: stringResource(R.string.detail_not_set),
            style = if (mono && value != null) FwTheme.type.monoS else FwTheme.type.label,
            color = if (value == null) FwTheme.colors.ink3 else FwTheme.colors.ink,
        )
    }
}

@Composable
private fun ActionRow(icon: Int, title: String, subtitle: String?, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = FwTheme.colors.bg) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint)
            Column {
                Text(title, style = FwTheme.type.body.copy(fontWeight = FontWeight.Medium), color = tint)
                subtitle?.let { Text(it, style = FwTheme.type.caption, color = FwTheme.colors.ink2) }
            }
        }
    }
}

@Composable
private fun DeleteDialog(
    vehicleName: String,
    matches: (String) -> Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FwTheme.colors.surface,
        title = { Text(stringResource(R.string.detail_delete_title, vehicleName), style = FwTheme.type.h3) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m)) {
                Text(
                    stringResource(R.string.detail_delete_body, vehicleName),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
                FwTextField(value = typed, onValueChange = {
                    typed = it
                }, label = stringResource(R.string.detail_delete_field))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = matches(typed)) {
                Text(
                    stringResource(R.string.detail_delete_confirm),
                    color = if (matches(typed)) FwTheme.colors.negative else FwTheme.colors.ink3,
                    style = FwTheme.type.label,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_cancel), color = FwTheme.colors.ink, style = FwTheme.type.label)
            }
        },
    )
}

private fun normName(norm: EmissionNorm): Int = when (norm) {
    EmissionNorm.BS3 -> R.string.norm_bs3
    EmissionNorm.BS4 -> R.string.norm_bs4
    EmissionNorm.BS6 -> R.string.norm_bs6
}

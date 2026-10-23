package app.fuelwise.feature.garage.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import app.fuelwise.core.ui.entry.EntryLabels
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
            photo = s.photo,
        )
    }
}

/** Vehicle detail (design.md screen 22). */
@Composable
internal fun VehicleDetailContent(
    vehicle: Vehicle,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    confirmationMatches: (String) -> Boolean,
    photo: ImageBitmap? = null,
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
            Header(vehicle, photo)
            Section(stringResource(R.string.detail_energy)) {
                vehicle.tanks.forEachIndexed { index, tank ->
                    if (index > 0) HorizontalDivider(color = FwTheme.colors.line)
                    Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(VehicleLabels.energyColor(tank.energyType), CircleShape))
                        Text(
                            stringResource(VehicleLabels.energy(tank.energyType)) +
                                tank.defaultGrade?.let { " · " + stringResource(EntryLabels.grade(it)) }.orEmpty() +
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
            Purchase(vehicle)
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
private fun Header(vehicle: Vehicle, photo: ImageBitmap?) {
    val card = Modifier.padding(horizontal = FwTheme.spacing.gutter).fillMaxWidth().clip(FwTheme.radius.card)
    if (photo == null) {
        // No photo: the type glyph on an ink block.
        Column(card.background(FwTheme.colors.ink).padding(FwTheme.spacing.l)) {
            Icon(
                painterResource(VehicleLabels.typeIcon(vehicle.type)),
                null,
                tint = FwTheme.colors.bg,
                modifier = Modifier.size(32.dp),
            )
            HeaderText(vehicle, Modifier.padding(top = 12.dp))
        }
        return
    }
    // The photo with the name over a scrim (design.md screen 22).
    Box(card.height(PHOTO_HEIGHT)) {
        Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        HeaderText(
            vehicle,
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(FwTheme.colors.ink.copy(alpha = 0f), FwTheme.colors.ink)))
                .padding(start = FwTheme.spacing.l, end = FwTheme.spacing.l, top = 40.dp, bottom = FwTheme.spacing.l),
        )
    }
}

@Composable
private fun HeaderText(vehicle: Vehicle, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(vehicle.name, style = FwTheme.type.h1, color = FwTheme.colors.bg)
        modelLine(vehicle)?.let { Text(it, style = FwTheme.type.bodyS, color = FwTheme.colors.bg) }
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

private val PHOTO_HEIGHT = 220.dp

/** Purchase details (design.md screen 22). */
@Composable
private fun Purchase(vehicle: Vehicle) {
    Section(stringResource(R.string.editor_purchase_section)) {
        ValueRow(
            stringResource(R.string.editor_purchase_date),
            vehicle.purchaseDate?.let {
                IndianNumberFormat.date(it)
            },
        )
        HorizontalDivider(color = FwTheme.colors.line)
        ValueRow(
            stringResource(R.string.editor_purchase_odometer),
            vehicle.purchaseOdometerKm?.let {
                stringResource(R.string.garage_km, IndianNumberFormat.decimal(it, 0))
            },
        )
        HorizontalDivider(color = FwTheme.colors.line)
        ValueRow(
            stringResource(R.string.editor_purchase_price),
            vehicle.purchasePrice?.let(IndianNumberFormat::rupeesWhole),
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

/** "Tata Safari XZ+ · 2023 · Hybrid", from what was filled in (PRD F-01.1, F-01.2). */
@Composable
private fun modelLine(vehicle: Vehicle): String? = listOfNotNull(
    listOfNotNull(vehicle.make, vehicle.model, vehicle.variant).joinToString(" ").ifEmpty { null },
    vehicle.year?.toString(),
    if (vehicle.isHybrid) stringResource(R.string.editor_hybrid) else null,
).joinToString(" · ").ifEmpty { null }

private fun normName(norm: EmissionNorm): Int = when (norm) {
    EmissionNorm.BS3 -> R.string.norm_bs3
    EmissionNorm.BS4 -> R.string.norm_bs4
    EmissionNorm.BS6 -> R.string.norm_bs6
}

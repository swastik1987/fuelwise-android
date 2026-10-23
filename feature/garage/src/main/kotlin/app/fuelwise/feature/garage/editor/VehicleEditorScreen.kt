package app.fuelwise.feature.garage.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwChoiceSheet
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSettingsRow
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.vehicle.VehicleDraftError
import app.fuelwise.core.location.rememberCarBluetoothLinker
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.VehicleType
import app.fuelwise.core.ui.entry.EntryDatePicker
import app.fuelwise.core.ui.entry.EntryLabels
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.NumberInput
import app.fuelwise.core.ui.vehicle.VehicleLabels
import app.fuelwise.feature.garage.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun VehicleEditorScreen(
    onBack: () -> Unit,
    onSaved: (vehicleId: String, onboarding: Boolean) -> Unit,
    viewModel: VehicleEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Null where Android can't observe a car (before Android 12): the row is hidden.
    val linkCar = rememberCarBluetoothLinker(viewModel::linkCar)
    // The Android photo picker: no storage permission (PRD §9).
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPhotoPicked)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VehicleEditorEvent.Saved -> onSaved(event.vehicleId, event.onboarding)
            }
        }
    }
    VehicleEditorContent(
        state = state,
        onBack = onBack,
        actions = VehicleEditorActions(
            onName = viewModel::onNameChange,
            onType = viewModel::onTypeChange,
            onFuel = viewModel::onFuelSetupChange,
            onOdometer = viewModel::onOdometerChange,
            onCapacity = viewModel::onCapacityChange,
            onRegistration = viewModel::onRegistrationChange,
            onEmissionNorm = viewModel::onEmissionNormChange,
            onClaimed = viewModel::onClaimedChange,
            onDetail = viewModel::onDetailChange,
            onPurchaseDate = viewModel::onPurchaseDateChange,
            onHybrid = viewModel::onHybridChange,
            onDefaultGrade = viewModel::onDefaultGradeChange,
            onPickPhoto = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = viewModel::onPhotoRemoved,
            onToggleMore = viewModel::onToggleMore,
            onSave = viewModel::save,
            onLinkCar = linkCar,
            onUnlinkCar = viewModel::unlinkCar,
        ),
    )
}

internal data class VehicleEditorActions(
    val onName: (String) -> Unit = {},
    val onType: (VehicleType) -> Unit = {},
    val onFuel: (FuelSetup) -> Unit = {},
    val onOdometer: (String) -> Unit = {},
    val onCapacity: (Int, String) -> Unit = { _, _ -> },
    val onRegistration: (String) -> Unit = {},
    val onEmissionNorm: (EmissionNorm) -> Unit = {},
    val onClaimed: (String) -> Unit = {},
    val onDetail: (VehicleDetail, String) -> Unit = { _, _ -> },
    val onPurchaseDate: (LocalDate?) -> Unit = {},
    val onHybrid: (Boolean) -> Unit = {},
    val onDefaultGrade: (FuelGrade) -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onToggleMore: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onLinkCar: (() -> Unit)? = null,
    val onUnlinkCar: () -> Unit = {},
)

/** Add your first vehicle / Add vehicle / Edit vehicle (design.md screen 2). A single scrolling form, not a wizard. */
@Composable
internal fun VehicleEditorContent(state: VehicleFormState, onBack: () -> Unit, actions: VehicleEditorActions) {
    val title = when {
        state.isEditing -> R.string.editor_title_edit
        state.onboarding -> R.string.editor_title_first
        else -> R.string.editor_title_add
    }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg).imePadding()) {
        FwTopBar(stringResource(title), onBack = onBack, backContentDescription = stringResource(R.string.editor_back))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
        ) {
            PhotoRow(state, actions)
            TypePicker(state.type, actions.onType)
            FuelPicker(state, actions.onFuel)
            FwTextField(
                value = state.name,
                onValueChange = actions.onName,
                label = stringResource(R.string.editor_name),
                placeholder = stringResource(R.string.editor_name_placeholder),
                error = state.errors.messageFor(VehicleDraftError.NameRequired, VehicleDraftError.NameTooLong),
                capitalization = KeyboardCapitalization.Words,
            )
            FwTextField(
                value = state.odometer,
                onValueChange = actions.onOdometer,
                label = stringResource(
                    if (state.isEditing) R.string.editor_start_odometer else R.string.editor_odometer,
                ),
                unit = stringResource(app.fuelwise.core.ui.R.string.unit_km),
                error = state.errors.messageFor(VehicleDraftError.OdometerRequired, VehicleDraftError.OdometerNegative),
                keyboardType = KeyboardType.Decimal,
                textStyle = FwTheme.type.mono,
            )
            MoreDetails(state, actions)
        }
        Box(Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s)) {
            FwPrimaryButton(
                text = stringResource(if (state.isEditing) R.string.editor_save else R.string.editor_add),
                onClick = actions.onSave,
                enabled = !state.isSaving && !state.isLoading,
            )
        }
    }
}

/** The vehicle's photo (PRD F-01.1): shown on its Garage card and detail header. */
@Composable
private fun PhotoRow(state: VehicleFormState, actions: VehicleEditorActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        Box(
            Modifier.size(PHOTO_SIZE).clip(FwTheme.radius.input).background(FwTheme.colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            val photo = state.photo
            if (photo != null) {
                Image(
                    photo,
                    stringResource(R.string.editor_photo),
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(painterResource(VehicleLabels.typeIcon(state.type)), null, tint = FwTheme.colors.ink3)
            }
        }
        Column(Modifier.weight(1f)) {
            FwTextButton(
                stringResource(if (state.photo == null) R.string.editor_photo_add else R.string.editor_photo_change),
                actions.onPickPhoto,
            )
            if (state.photo != null) FwTextButton(stringResource(R.string.editor_photo_remove), actions.onRemovePhoto)
            if (state.photoFailed) {
                Text(
                    stringResource(R.string.editor_photo_failed),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.negative,
                )
            }
        }
    }
}

@Composable
private fun TypePicker(selected: VehicleType, onSelect: (VehicleType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s)) {
        FwOverline(stringResource(R.string.editor_type))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
        ) {
            PICKER_TYPES.forEach { type -> TypeTile(type, type == selected) { onSelect(type) } }
        }
    }
}

@Composable
private fun TypeTile(type: VehicleType, selected: Boolean, onClick: () -> Unit) {
    val colors = FwTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.width(76.dp).heightIn(min = 84.dp).semantics {
            this.selected = selected
            role = Role.RadioButton
        },
        shape = FwTheme.radius.input,
        color = if (selected) colors.surface else colors.surface2,
        contentColor = if (selected) colors.ink else colors.ink2,
        border = if (selected) BorderStroke(1.5.dp, colors.ink) else null,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(VehicleLabels.typeIcon(type)),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(stringResource(VehicleLabels.type(type)), style = FwTheme.type.caption)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelPicker(state: VehicleFormState, onSelect: (FuelSetup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s)) {
        FwOverline(stringResource(R.string.editor_fuel))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelSetup.entries.forEach { setup ->
                // In edit mode only the current setup is shown: fuel can't change once entries may exist.
                if (!state.isEditing || setup == state.fuelSetup) {
                    FwFilterChip(
                        label = stringResource(VehicleLabels.fuelSetup(setup)),
                        selected = setup == state.fuelSetup,
                        onClick = { onSelect(setup) },
                    )
                }
            }
        }
        if (state.isEditing) {
            Text(stringResource(R.string.editor_fuel_locked), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
        }
    }
}

/** "Car Bluetooth for automatic trips" (design.md screen 22): link through the system chooser, or unlink. */
@Composable
private fun CarBluetoothRow(state: VehicleFormState, onLink: () -> Unit, onUnlink: () -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    FwSettingsRow(
        stringResource(R.string.editor_car_bluetooth),
        value = when {
            !state.carLinked -> stringResource(R.string.editor_car_bluetooth_none)
            else -> state.carLinkName ?: stringResource(R.string.editor_car_bluetooth_linked)
        },
        onClick = { if (state.carLinked) choosing = true else onLink() },
    )
    if (choosing) {
        val options = listOf(
            R.string.editor_car_bluetooth_change to onLink,
            R.string.editor_car_bluetooth_unlink to onUnlink,
        )
        FwChoiceSheet(
            title = stringResource(R.string.editor_car_bluetooth),
            options = options.map { stringResource(it.first) },
            selectedIndex = -1,
            onSelect = {
                choosing = false
                options[it].second()
            },
            onDismiss = { choosing = false },
            subtitle = stringResource(R.string.editor_car_bluetooth_hint),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreDetails(state: VehicleFormState, actions: VehicleEditorActions) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m)) {
        HorizontalDivider(color = FwTheme.colors.line)
        Surface(onClick = actions.onToggleMore, color = FwTheme.colors.bg) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_more), style = FwTheme.type.label, color = FwTheme.colors.ink)
                    Text(
                        stringResource(R.string.editor_more_hint),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink2,
                    )
                }
                Icon(
                    painterResource(FwIcons.ExpandMore),
                    contentDescription = null,
                    tint = FwTheme.colors.ink2,
                    modifier = Modifier.rotate(if (state.moreExpanded) EXPANDED_ROTATION else 0f),
                )
            }
        }
        if (state.moreExpanded) {
            state.fuelSetup.tanks.forEachIndexed { index, energy ->
                val unit = stringResource(VehicleLabels.unit(energy.canonicalUnit))
                FwTextField(
                    value = state.capacities.getOrElse(index) { "" },
                    onValueChange = { actions.onCapacity(index, it) },
                    label = stringResource(
                        if (state.fuelSetup == FuelSetup.Electric) R.string.editor_battery else R.string.editor_tank,
                        stringResource(VehicleLabels.energy(energy)),
                    ),
                    unit = unit,
                    keyboardType = KeyboardType.Decimal,
                    error = if (VehicleDraftError.CapacityNotPositive in state.errors && state.capacityInvalid(index)) {
                        stringResource(R.string.editor_error_capacity)
                    } else {
                        null
                    },
                )
            }
            if (state.fuelSetup.isBiFuel) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(
                            8.dp,
                        ).background(VehicleLabels.energyColor(state.fuelSetup.tanks.first()), CircleShape),
                    )
                    Text(
                        stringResource(R.string.editor_bifuel_hint),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink2,
                    )
                }
            }
            FwTextField(
                value = state.registration,
                onValueChange = actions.onRegistration,
                label = stringResource(R.string.editor_registration),
                placeholder = stringResource(R.string.editor_registration_placeholder),
                error = state.errors.messageFor(VehicleDraftError.RegistrationInvalid),
                capitalization = KeyboardCapitalization.Characters,
                textStyle = FwTheme.type.body.copy(fontFamily = FwTheme.type.mono.fontFamily),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.editor_emission_norm),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmissionNorm.entries.forEach { norm ->
                        FwChoiceChip(stringResource(normLabel(norm)), norm == state.emissionNorm, {
                            actions.onEmissionNorm(norm)
                        })
                    }
                }
            }
            FwTextField(
                value = state.claimed,
                onValueChange = actions.onClaimed,
                label = stringResource(R.string.editor_claimed),
                unit = stringResource(
                    R.string.editor_claimed_unit,
                    stringResource(VehicleLabels.unit(state.fuelSetup.tanks.first().canonicalUnit)),
                ),
                error = state.errors.messageFor(VehicleDraftError.ClaimedNotPositive),
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            )
            if (state.hasPetrolTank) PetrolDetails(state, actions)
            ModelDetails(state, actions.onDetail)
            PurchaseDetails(state, actions)
            actions.onLinkCar?.takeIf {
                state.showCarBluetooth
            }?.let { link -> CarBluetoothRow(state, link, actions.onUnlinkCar) }
        }
    }
}

/** Default grade and the hybrid tag (PRD F-01.2). Hybrids are single-fuel petrol. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetrolDetails(state: VehicleFormState, actions: VehicleEditorActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.editor_default_grade), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelGrade.entries.forEach { grade ->
                FwChoiceChip(stringResource(EntryLabels.grade(grade)), grade == state.defaultGrade, {
                    actions.onDefaultGrade(grade)
                })
            }
        }
    }
    if (state.fuelSetup == FuelSetup.Petrol) {
        FwSwitchRow(
            title = stringResource(R.string.editor_hybrid),
            checked = state.isHybrid,
            onCheckedChange = actions.onHybrid,
            subtitle = stringResource(R.string.editor_hybrid_hint),
        )
    }
}

/** Make, model, variant and year: free text in R1 (PRD F-01.1). */
@Composable
private fun ModelDetails(state: VehicleFormState, onDetail: (VehicleDetail, String) -> Unit) {
    FwOverline(stringResource(R.string.editor_model_section))
    Row(horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
        DetailField(state, VehicleDetail.Make, R.string.editor_make, onDetail, Modifier.weight(1f))
        DetailField(state, VehicleDetail.Model, R.string.editor_model, onDetail, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
        DetailField(state, VehicleDetail.Variant, R.string.editor_variant, onDetail, Modifier.weight(1f))
        DetailField(
            state,
            VehicleDetail.Year,
            R.string.editor_year,
            onDetail,
            Modifier.width(YEAR_WIDTH),
            error = state.errors.messageFor(VehicleDraftError.YearInvalid),
        )
    }
}

/** Purchase date, reading and price (design.md screen 22 "Purchase details"). */
@Composable
private fun PurchaseDetails(state: VehicleFormState, actions: VehicleEditorActions) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    FwOverline(stringResource(R.string.editor_purchase_section))
    FwSettingsRow(
        stringResource(R.string.editor_purchase_date),
        value =
            state.purchaseDate?.let { IndianNumberFormat.date(it) }
                ?: stringResource(R.string.editor_purchase_date_none),
        onClick = { pickingDate = true },
    )
    state.errors.messageFor(VehicleDraftError.PurchaseInFuture)?.let {
        Text(it, style = FwTheme.type.caption, color = FwTheme.colors.negative)
    }
    DetailField(
        state,
        VehicleDetail.PurchaseOdometer,
        R.string.editor_purchase_odometer,
        actions.onDetail,
        unit = stringResource(app.fuelwise.core.ui.R.string.unit_km),
        error = state.errors.messageFor(VehicleDraftError.PurchaseOdometerInvalid),
    )
    DetailField(
        state,
        VehicleDetail.PurchasePrice,
        R.string.editor_purchase_price,
        actions.onDetail,
        unit = "₹",
        error = state.errors.messageFor(VehicleDraftError.PurchasePriceNotPositive),
    )
    if (pickingDate) {
        val initial = (state.purchaseDate ?: LocalDate.now(zone)).atStartOfDay(zone).toInstant().toEpochMilli()
        EntryDatePicker(initial, zone, {
            actions.onPurchaseDate(Instant.ofEpochMilli(it).atZone(zone).toLocalDate())
            pickingDate = false
        }, { pickingDate = false })
    }
}

@Composable
private fun DetailField(
    state: VehicleFormState,
    field: VehicleDetail,
    label: Int,
    onDetail: (VehicleDetail, String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    error: String? = null,
) {
    val numeric = field == VehicleDetail.Year || field == VehicleDetail.PurchaseOdometer ||
        field == VehicleDetail.PurchasePrice
    FwTextField(
        value = state.detail(field),
        onValueChange = { onDetail(field, it) },
        label = stringResource(label),
        modifier = modifier,
        unit = unit,
        error = error,
        keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
        capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Words,
    )
}

private fun VehicleFormState.capacityInvalid(index: Int): Boolean {
    val text = capacities.getOrNull(index).orEmpty()
    return text.isNotEmpty() && (NumberInput.parseDecimal(text) ?: 0.0) <= 0.0
}

@Composable
private fun Set<VehicleDraftError>.messageFor(vararg candidates: VehicleDraftError): String? =
    candidates.firstOrNull { it in this }?.let { stringResource(errorMessage(it)) }

private fun errorMessage(error: VehicleDraftError): Int = when (error) {
    VehicleDraftError.NameRequired -> R.string.editor_error_name
    VehicleDraftError.NameTooLong -> R.string.editor_error_name_long
    VehicleDraftError.OdometerRequired -> R.string.editor_error_odometer
    VehicleDraftError.OdometerNegative -> R.string.editor_error_odometer_negative
    VehicleDraftError.CapacityNotPositive -> R.string.editor_error_capacity
    VehicleDraftError.RegistrationInvalid -> R.string.editor_error_registration
    VehicleDraftError.ClaimedNotPositive -> R.string.editor_error_claimed
    VehicleDraftError.YearInvalid -> R.string.editor_error_year
    VehicleDraftError.PurchaseInFuture -> R.string.editor_error_purchase_date
    VehicleDraftError.PurchaseOdometerInvalid -> R.string.editor_error_purchase_odometer
    VehicleDraftError.PurchasePriceNotPositive -> R.string.editor_error_purchase_price
}

private fun normLabel(norm: EmissionNorm): Int = when (norm) {
    EmissionNorm.BS3 -> R.string.norm_bs3
    EmissionNorm.BS4 -> R.string.norm_bs4
    EmissionNorm.BS6 -> R.string.norm_bs6
}

/** Tiles shown on the form, in design order. Van and truck share one tile; "Other" lives under edit. */
private val PICKER_TYPES = listOf(
    VehicleType.Car,
    VehicleType.Scooter,
    VehicleType.Motorcycle,
    VehicleType.AutoRickshaw,
    VehicleType.Truck,
)

private const val EXPANDED_ROTATION = 180f
private val YEAR_WIDTH = 104.dp
private val PHOTO_SIZE = 72.dp

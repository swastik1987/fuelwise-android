package app.fuelwise.feature.documents

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwHeaderChip
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.PolicyType
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.entry.EntryDatePicker
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleSwitcherSheet
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun DocumentEditorScreen(onClose: () -> Unit, viewModel: DocumentEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val failed = stringResource(R.string.editor_attach_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DocumentEditorEvent.Closed -> onClose()
                DocumentEditorEvent.AttachFailed -> Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    // The Android photo picker: no storage permission (PRD §9). PDFs come through the system file picker.
    val photo =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::attach)
        }
    val pdf =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::attach) }
    BackHandler { viewModel.discard() }
    DocumentEditorContent(
        state = state,
        attachments = attachments,
        onUpdate = viewModel::update,
        onAddPhoto = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAddPdf = { pdf.launch(arrayOf(AttachmentPreviews.PDF)) },
        onRemove = viewModel::removeAttachment,
        onSave = viewModel::save,
        onClose = viewModel::discard,
        onDelete = viewModel::delete,
    )
}

/** Add document (design.md screen 24): type → photo or PDF → fields → expiry with the reminder toggle shown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocumentEditorContent(
    state: DocumentForm,
    attachments: List<AttachmentPreview>,
    onUpdate: ((DocumentForm) -> DocumentForm) -> Unit,
    onAddPhoto: () -> Unit,
    onAddPdf: () -> Unit,
    onRemove: (Attachment) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var switcher by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(if (state.isEdit) R.string.editor_edit else R.string.editor_add),
            onBack = onClose,
            backContentDescription = stringResource(R.string.documents_back),
        )
        if (state.loading) return@Column
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.l),
        ) {
            FwOverline(stringResource(R.string.editor_type))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DocumentType.entries.forEach { type ->
                    FwFilterChip(stringResource(CareLabels.document(type)), type == state.type, {
                        onUpdate { it.copy(type = type) }
                    })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.editor_vehicle),
                    style = FwTheme.type.body,
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                FwHeaderChip(
                    state.vehicles.firstOrNull { it.id == state.vehicleId }?.name
                        ?: stringResource(R.string.editor_no_vehicle),
                    onClick = { switcher = true },
                    leadingIcon = FwIcons.Car,
                )
            }
            Attachments(attachments, onAddPhoto, onAddPdf, onRemove)
            FwTextField(
                value = state.number,
                onValueChange = { v -> onUpdate { it.copy(number = v.take(MAX_FIELD)) } },
                label = stringResource(R.string.editor_number),
                capitalization = KeyboardCapitalization.Characters,
            )
            FwTextField(
                value = state.issuer,
                onValueChange = { v -> onUpdate { it.copy(issuer = v.take(MAX_FIELD)) } },
                label = stringResource(R.string.editor_issuer),
            )
            DateRow(stringResource(R.string.editor_issue_date), state.issueDateUtcMs, zone, {
                picking = ISSUE
            }, { onUpdate { it.copy(issueDateUtcMs = null) } })
            DateRow(stringResource(R.string.editor_expiry_date), state.expiryDateUtcMs, zone, {
                picking = EXPIRY
            }, { onUpdate { it.copy(expiryDateUtcMs = null) } })
            if (state.expiryDateUtcMs != null) {
                FwSwitchRow(
                    title = stringResource(R.string.editor_remind),
                    subtitle = stringResource(
                        if (state.vehicleId ==
                            null
                        ) {
                            R.string.editor_remind_needs_vehicle
                        } else {
                            R.string.editor_remind_body
                        },
                    ),
                    checked = state.remindOnExpiry,
                    onCheckedChange = { v -> onUpdate { it.copy(remindOnExpiry = v) } },
                )
            }
            if (state.type == DocumentType.Insurance) InsuranceFields(state, onUpdate)
            FwTextField(
                value = state.notes,
                onValueChange = { v -> onUpdate { it.copy(notes = v) } },
                label = stringResource(R.string.editor_notes),
                singleLine = false,
            )
            FwInkButton(stringResource(R.string.editor_save), onSave, Modifier.fillMaxWidth())
            if (state.isEdit) {
                FwSecondaryButton(
                    stringResource(R.string.editor_delete),
                    onDelete,
                    Modifier.fillMaxWidth(),
                    leadingIcon = FwIcons.Delete,
                )
            }
            Box(Modifier.height(24.dp))
        }
    }
    picking?.let { which ->
        EntryDatePicker(
            initialUtcMs = (if (which == ISSUE) state.issueDateUtcMs else state.expiryDateUtcMs) ?: state.nowUtcMs,
            zone = zone,
            onPicked = { ms ->
                onUpdate { if (which == ISSUE) it.copy(issueDateUtcMs = ms) else it.copy(expiryDateUtcMs = ms) }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    if (switcher) {
        VehicleSwitcherSheet(
            vehicles = state.vehicles,
            selectedId = state.vehicleId,
            onSelect = { id ->
                onUpdate { it.copy(vehicleId = id) }
                switcher = false
            },
            onAddVehicle = { switcher = false },
            onDismiss = { switcher = false },
        )
    }
}

@Composable
private fun Attachments(
    attachments: List<AttachmentPreview>,
    onAddPhoto: () -> Unit,
    onAddPdf: () -> Unit,
    onRemove: (Attachment) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FwOverline(stringResource(R.string.editor_files))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { preview ->
                Box(Modifier.size(THUMB.dp).clip(FwTheme.radius.card).background(FwTheme.colors.surface2)) {
                    val image = preview.image
                    if (image != null) {
                        Image(
                            image,
                            stringResource(R.string.view_attachment),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painterResource(FwIcons.UploadFile),
                            null,
                            tint = FwTheme.colors.ink2,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    IconButton(onClick = {
                        onRemove(preview.attachment)
                    }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(
                            painterResource(FwIcons.Close),
                            stringResource(R.string.editor_remove_file),
                            tint = FwTheme.colors.ink,
                            modifier = Modifier.size(18.dp).background(FwTheme.colors.surface, FwTheme.radius.pill),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FwSecondaryButton(
                stringResource(R.string.editor_add_photo),
                onAddPhoto,
                Modifier.weight(1f),
                leadingIcon = FwIcons.Camera,
            )
            FwSecondaryButton(
                stringResource(R.string.editor_add_pdf),
                onAddPdf,
                Modifier.weight(1f),
                leadingIcon = FwIcons.UploadFile,
            )
        }
        Text(stringResource(R.string.editor_files_encrypted), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsuranceFields(state: DocumentForm, onUpdate: ((DocumentForm) -> DocumentForm) -> Unit) {
    FwOverline(stringResource(R.string.editor_insurance))
    FwTextField(value = state.insurer, onValueChange = { v ->
        onUpdate { it.copy(insurer = v.take(MAX_FIELD)) }
    }, label = stringResource(R.string.editor_insurer))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PolicyType.entries.forEach { type ->
            FwFilterChip(stringResource(CareLabels.policy(type)), type == state.policyType, {
                onUpdate { it.copy(policyType = if (it.policyType == type) null else type) }
            })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FwTextField(
            value = state.idv,
            onValueChange = { v ->
                onUpdate { it.copy(idv = v.filter { c -> c.isDigit() || c == '.' }.take(MAX_AMOUNT)) }
            },
            label = stringResource(R.string.editor_idv),
            unit = "₹",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        FwTextField(
            value = state.ncb,
            onValueChange = { v -> onUpdate { it.copy(ncb = v.filter(Char::isDigit).take(2)) } },
            label = stringResource(R.string.editor_ncb),
            unit = "%",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
    }
    FwTextField(value = state.addOns, onValueChange = { v ->
        onUpdate { it.copy(addOns = v) }
    }, label = stringResource(R.string.editor_add_ons), singleLine = false)
}

@Composable
private fun DateRow(label: String, value: Long?, zone: ZoneId, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
            Text(
                value?.let { IndianNumberFormat.date(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) } ?: "—",
                style = FwTheme.type.body,
                color = FwTheme.colors.ink,
            )
        }
        FwTextButton(stringResource(R.string.editor_pick_date), onPick)
        if (value != null) FwTextButton(stringResource(R.string.editor_clear), onClear)
    }
}

private const val ISSUE = "issue"
private const val EXPIRY = "expiry"
private const val MAX_FIELD = 60
private const val MAX_AMOUNT = 12
private const val THUMB = 88

package app.fuelwise.feature.dataimport

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.importing.DateOrder
import app.fuelwise.core.domain.importing.EntryKind
import app.fuelwise.core.domain.importing.ImportField
import app.fuelwise.core.domain.importing.ImportPlanner
import app.fuelwise.core.domain.importing.RowIssue
import app.fuelwise.core.domain.importing.RowProblem
import app.fuelwise.core.model.ImportBatch
import app.fuelwise.core.model.ImportSource
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the screen can ask for; the route wires these to the view model and the system file pickers. */
internal data class ImportActions(
    val onBack: () -> Unit = {},
    val onPickFile: () -> Unit = {},
    val onSaveTemplate: () -> Unit = {},
    val onUndo: (ImportBatch) -> Unit = {},
    val onKind: (EntryKind) -> Unit = {},
    val onColumn: (ImportField, Int?) -> Unit = { _, _ -> },
    val onDateOrder: (DateOrder) -> Unit = {},
    val onConfirmMapping: () -> Unit = {},
    val onVehicle: (String) -> Unit = {},
    val onConfirmImport: () -> Unit = {},
)

@Composable
internal fun ImportScreen(onBack: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickFile)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        uri?.let(viewModel::saveTemplate)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Imported -> {
                    val n = event.batch.entryCount
                    val result = snackbar.showSnackbar(
                        message = resources.getQuantityString(R.plurals.import_done, n, n),
                        actionLabel = resources.getString(R.string.import_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.batch.id)
                }

                ImportEvent.Undone -> snackbar.showSnackbar(resources.getString(R.string.import_undone))

                is ImportEvent.TemplateSaved -> Toast.makeText(
                    context,
                    resources.getString(
                        if (event.ok) R.string.import_template_saved else R.string.import_template_failed,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val inFlow = state.step == ImportStep.Mapping || state.step == ImportStep.Preview
    BackHandler(enabled = inFlow, onBack = viewModel::back)
    Box(Modifier.fillMaxSize()) {
        ImportContent(
            state,
            ImportActions(
                onBack = if (inFlow) viewModel::back else onBack,
                onPickFile = { open.launch(PICKABLE_TYPES) },
                onSaveTemplate = { save.launch(viewModel.templateFileName) },
                onUndo = { viewModel.undo(it.id) },
                onKind = viewModel::setKind,
                onColumn = viewModel::setColumn,
                onDateOrder = viewModel::setDateOrder,
                onConfirmMapping = viewModel::confirmMapping,
                onVehicle = viewModel::setVehicle,
                onConfirmImport = viewModel::confirmImport,
            ),
        )
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** Import data (PRD F-13.2): start, column mapper, preview. */
@Composable
internal fun ImportContent(state: ImportUiState, actions: ImportActions, zone: ZoneId = ZoneId.systemDefault()) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        val title = when (state.step) {
            ImportStep.Mapping -> R.string.import_map_title
            ImportStep.Preview -> R.string.import_preview_title
            else -> R.string.import_title
        }
        FwTopBar(
            title = stringResource(title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.import_back),
        )
        when (state.step) {
            ImportStep.Start -> StartStep(state, actions, zone)
            ImportStep.Reading -> Busy(stringResource(R.string.import_reading))
            ImportStep.Saving -> Busy(stringResource(R.string.import_saving))
            ImportStep.Mapping -> state.mapper?.let { MappingStep(it, actions) }
            ImportStep.Preview -> state.preview?.let { PreviewStep(it, state, actions) }
        }
    }
}

@Composable
internal fun ScrollingBody(bottom: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = FwTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.l),
            content = {
                content()
                Box(Modifier.height(FwTheme.spacing.l))
            },
        )
        bottom?.let {
            Box(
                Modifier.padding(
                    horizontal = FwTheme.spacing.gutter,
                    vertical = FwTheme.spacing.s,
                ).navigationBarsPadding(),
            ) { it() }
        }
    }
}

@Composable
private fun Busy(label: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = FwTheme.colors.ink, strokeWidth = 3.dp)
        Text(label, style = FwTheme.type.body, color = FwTheme.colors.ink2, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun StartStep(state: ImportUiState, actions: ImportActions, zone: ZoneId) {
    var undoing by remember { mutableStateOf<ImportBatch?>(null) }
    ScrollingBody {
        Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
            Text(stringResource(R.string.import_pick_title), style = FwTheme.type.h2, color = FwTheme.colors.ink)
            Text(stringResource(R.string.import_pick_body), style = FwTheme.type.body, color = FwTheme.colors.ink2)
        }
        FwCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
            KindRow(FwIcons.Spreadsheet, R.string.import_kind_template, R.string.import_kind_template_sub)
            KindRow(FwIcons.UploadFile, R.string.import_kind_fuelio, R.string.import_kind_fuelio_sub)
            KindRow(FwIcons.Document, R.string.import_kind_other, R.string.import_kind_other_sub)
        }
        state.error?.let { Banner(stringResource(errorText(it))) }
        FwInkButton(stringResource(R.string.import_pick), actions.onPickFile, Modifier.fillMaxWidth())

        FwOverline(stringResource(R.string.import_template_section))
        Text(stringResource(R.string.import_template_body), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
        FwSecondaryButton(
            stringResource(R.string.import_template_save),
            actions.onSaveTemplate,
            Modifier.fillMaxWidth(),
        )

        if (state.batches.isNotEmpty()) {
            FwOverline(stringResource(R.string.import_recent))
            Column {
                state.batches.forEach { batch -> BatchRow(batch, zone, onUndo = { undoing = batch }) }
            }
        }
    }
    undoing?.let { batch ->
        AlertDialog(
            onDismissRequest = { undoing = null },
            containerColor = FwTheme.colors.surface,
            title = { Text(stringResource(R.string.import_undo_title), style = FwTheme.type.h3) },
            text = {
                Text(
                    pluralStringResource(R.plurals.import_undo_body, batch.entryCount, batch.entryCount),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    undoing = null
                    actions.onUndo(batch)
                }) { Text(stringResource(R.string.import_undo_confirm), color = FwTheme.colors.negative) }
            },
            dismissButton = {
                TextButton(onClick = { undoing = null }) {
                    Text(stringResource(R.string.import_cancel), color = FwTheme.colors.ink)
                }
            },
        )
    }
}

@Composable
private fun KindRow(icon: Int, title: Int, subtitle: Int) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).background(FwTheme.colors.surface2, FwTheme.radius.tile), Alignment.Center) {
            Icon(painterResource(icon), null, tint = FwTheme.colors.ink, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(title),
                style = FwTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = FwTheme.colors.ink,
            )
            Text(stringResource(subtitle), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
        }
    }
}

@Composable
private fun BatchRow(batch: ImportBatch, zone: ZoneId, onUndo: () -> Unit) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ENGLISH
    val date = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", locale)
        .format(Instant.ofEpochMilli(batch.importedAtUtcMs).atZone(zone))
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(stringResource(sourceLabel(batch.source)), batch.fileName).joinToString(" · "),
                style = FwTheme.type.body,
                color = FwTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$date · " + pluralStringResource(R.plurals.import_entries, batch.entryCount, batch.entryCount),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
            )
        }
        TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.import_undo), style = FwTheme.type.label, color = FwTheme.colors.ink)
        }
    }
}

@Composable
private fun Banner(text: String) {
    FwCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(text, style = FwTheme.type.body, color = FwTheme.colors.negative)
    }
}

@Composable
private fun MappingStep(mapper: MapperState, actions: ImportActions) {
    var picking by rememberSaveable { mutableStateOf<ImportField?>(null) }
    val kind = mapper.mapping.kind
    ScrollingBody(
        bottom = {
            FwInkButton(
                stringResource(R.string.import_map_continue),
                actions.onConfirmMapping,
                Modifier.fillMaxWidth(),
                enabled = mapper.mapping.isComplete,
            )
        },
    ) {
        Text(stringResource(R.string.import_map_body), style = FwTheme.type.body, color = FwTheme.colors.ink2)
        FwOverline(stringResource(R.string.import_map_kind))
        FwSegmentedControl(
            options = listOf(stringResource(R.string.import_kind_fuel), stringResource(R.string.import_kind_expense)),
            selectedIndex = EntryKind.entries.indexOf(kind),
            onSelect = { actions.onKind(EntryKind.entries[it]) },
        )
        FwOverline(stringResource(R.string.import_map_dates))
        FwSegmentedControl(
            options = listOf(stringResource(R.string.import_date_dmy), stringResource(R.string.import_date_mdy)),
            selectedIndex = DateOrder.entries.indexOf(mapper.dateOrder),
            onSelect = { actions.onDateOrder(DateOrder.entries[it]) },
        )
        FwCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
            ImportField.of(kind).forEach { field ->
                FieldRow(field, mapper, required = field in ImportField.required(kind), onClick = { picking = field })
            }
        }
        if (kind == EntryKind.Fuel && !mapper.mapping.isComplete) {
            Text(stringResource(R.string.import_map_need_two), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
        }
    }
    picking?.let { field ->
        ColumnPicker(field, mapper, onDismiss = { picking = null }) { column ->
            actions.onColumn(field, column)
            picking = null
        }
    }
}

@Composable
private fun FieldRow(field: ImportField, mapper: MapperState, required: Boolean, onClick: () -> Unit) {
    val column = mapper.mapping[field]
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(ImportTexts.fieldLabel(field)), style = FwTheme.type.body, color = FwTheme.colors.ink)
            if (required) {
                Text(
                    stringResource(R.string.import_map_required),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink3,
                )
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                column?.let { columnName(mapper, it) } ?: stringResource(R.string.import_map_none),
                style = FwTheme.type.bodyS.copy(
                    fontWeight = if (column !=
                        null
                    ) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Normal
                    },
                ),
                color = if (column != null) FwTheme.colors.ink else FwTheme.colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            column?.let { mapper.samples.getOrNull(it) }?.let { sample ->
                Text(
                    stringResource(R.string.import_map_sample, sample),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            painterResource(FwIcons.ExpandMore),
            null,
            tint = FwTheme.colors.ink3,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun columnName(mapper: MapperState, column: Int): String =
    mapper.headers.getOrNull(column)?.ifBlank { null } ?: stringResource(R.string.import_map_column, column + 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnPicker(field: ImportField, mapper: MapperState, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter).navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.import_map_pick, stringResource(ImportTexts.fieldLabel(field))),
                style = FwTheme.type.h3,
                color = FwTheme.colors.ink,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            PickRow(stringResource(R.string.import_map_none), null, selected = mapper.mapping[field] == null) {
                onPick(null)
            }
            mapper.headers.indices.forEach { c ->
                PickRow(columnName(mapper, c), mapper.samples.getOrNull(c), selected = mapper.mapping[field] == c) {
                    onPick(c)
                }
            }
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PickRow(title: String, sample: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = FwTheme.type.body,
                color = FwTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sample?.let {
                Text(
                    stringResource(R.string.import_map_sample, it),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) Icon(painterResource(FwIcons.Check), null, tint = FwTheme.colors.ink)
    }
}

internal fun sourceLabel(source: ImportSource): Int = when (source) {
    ImportSource.Template -> R.string.import_source_template
    ImportSource.Fuelio -> R.string.import_source_fuelio
    ImportSource.Spreadsheet -> R.string.import_source_spreadsheet
}

private fun errorText(error: ImportError): Int = when (error) {
    ImportError.Unreadable -> R.string.import_error_unreadable
    ImportError.NoVehicles -> R.string.import_error_no_vehicle
    ImportError.Empty -> R.string.import_error_empty
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Spreadsheets and CSVs; some file managers label CSVs as plain text or generic binary. */
private val PICKABLE_TYPES = arrayOf(
    XLSX_MIME,
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "text/plain",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

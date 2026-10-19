package app.fuelwise.feature.dataimport

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the screen can ask for; the route wires these to the view model, the file picker and share sheet. */
internal data class ExportActions(
    val onBack: () -> Unit = {},
    val onExportVehicle: (String?) -> Unit = {},
    val onRange: (DataRange) -> Unit = {},
    val onExport: () -> Unit = {},
    val onReportKind: (ReportKind) -> Unit = {},
    val onReportVehicle: (String?) -> Unit = {},
    val onSpan: (ReportSpan) -> Unit = {},
    val onStep: (Boolean) -> Unit = {},
    val onRate: (String) -> Unit = {},
    val onShare: (Report) -> Unit = {},
    val onSaveCsv: () -> Unit = {},
)

@Composable
internal fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = currentLocale()
    val snackbar = remember { SnackbarHostState() }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) {
        viewModel.writeExport(it)
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        uri?.let(viewModel::writeReport)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportEvent.PickExportFile -> saveZip.launch(event.fileName)

                is ExportEvent.PickReportFile -> saveCsv.launch(event.fileName)

                is ExportEvent.Exported -> snackbar.showSnackbar(
                    resources.getQuantityString(R.plurals.export_done, event.files, event.files),
                )

                is ExportEvent.ReportSaved -> snackbar.showSnackbar(
                    resources.getString(if (event.ok) R.string.report_saved else R.string.report_save_failed),
                )
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        ExportContent(
            state,
            ExportActions(
                onBack = onBack,
                onExportVehicle = viewModel::toggleExportVehicle,
                onRange = viewModel::setRange,
                onExport = viewModel::startExport,
                onReportKind = viewModel::setReportKind,
                onReportVehicle = viewModel::setReportVehicle,
                onSpan = viewModel::setSpan,
                onStep = viewModel::stepPeriod,
                onRate = viewModel::setRate,
                onShare = { report ->
                    val formatter = ReportFormatter(resources, state.vehicles, state.categories, locale)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, formatter.title(report))
                        putExtra(Intent.EXTRA_TEXT, formatter.text(report))
                    }
                    context.startActivity(Intent.createChooser(send, resources.getString(R.string.report_share)))
                },
                onSaveCsv = viewModel::saveReportCsv,
            ),
        )
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ENGLISH

/** Export & reports (PRD F-13.1, F-13.3): CSV export of everything or a range, then the free reports. */
@Composable
internal fun ExportContent(state: ExportUiState, actions: ExportActions) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.export_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.import_back),
        )
        if (!state.loading && !state.hasVehicles) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                FwEmptyState(
                    icon = FwIcons.Download,
                    headline = stringResource(R.string.export_empty_title),
                    body = stringResource(R.string.export_empty_body),
                )
            }
            return@Column
        }
        ScrollingBody {
            ExportSection(state, actions)
            ReportSection(state, actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportSection(state: ExportUiState, actions: ExportActions) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
        Text(
            stringResource(R.string.export_csv_title),
            style = FwTheme.type.h2,
            color = FwTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.export_csv_body), style = FwTheme.type.body, color = FwTheme.colors.ink2)
    }
    FwOverline(stringResource(R.string.export_vehicles))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FwFilterChip(
            stringResource(R.string.export_all_vehicles),
            selected = state.exportVehicleIds == null,
            onClick = { actions.onExportVehicle(null) },
        )
        state.vehicles.forEach { v ->
            FwFilterChip(
                v.name,
                selected = state.exportVehicleIds?.contains(v.id) == true,
                onClick = { actions.onExportVehicle(v.id) },
            )
        }
    }
    FwOverline(stringResource(R.string.export_range))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DataRange.entries.forEach { range ->
            FwFilterChip(stringResource(rangeLabel(range)), state.range == range, { actions.onRange(range) })
        }
    }
    state.problem?.let { problem ->
        FwCard(
            Modifier.fillMaxWidth().semantics {
                liveRegion = LiveRegionMode.Polite
            },
            contentPadding = PaddingValues(16.dp),
        ) {
            Text(
                stringResource(
                    when (problem) {
                        ExportProblem.NothingToExport -> R.string.export_nothing
                        ExportProblem.WriteFailed -> R.string.export_failed
                    },
                ),
                style = FwTheme.type.body,
                color = if (problem == ExportProblem.WriteFailed) FwTheme.colors.negative else FwTheme.colors.ink,
            )
        }
    }
    FwInkButton(
        stringResource(if (state.exporting) R.string.export_working else R.string.export_csv_action),
        actions.onExport,
        Modifier.fillMaxWidth(),
        enabled = !state.exporting,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportSection(state: ExportUiState, actions: ExportActions) {
    Column(
        Modifier.padding(top = FwTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
    ) {
        Text(
            stringResource(R.string.report_section_title),
            style = FwTheme.type.h2,
            color = FwTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.report_section_body), style = FwTheme.type.body, color = FwTheme.colors.ink2)
    }
    FwSegmentedControl(
        options = ReportKind.entries.map { stringResource(kindLabel(it)) },
        selectedIndex = ReportKind.entries.indexOf(state.reportKind),
        onSelect = { actions.onReportKind(ReportKind.entries[it]) },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.reportKind == ReportKind.Business) {
            FwFilterChip(
                stringResource(R.string.export_all_vehicles),
                selected = state.reportVehicleId == null,
                onClick = { actions.onReportVehicle(null) },
            )
        }
        state.vehicles.forEach { v ->
            FwFilterChip(v.name, state.reportVehicleId == v.id, { actions.onReportVehicle(v.id) })
        }
    }
    if (state.reportKind != ReportKind.Seller) {
        FwSegmentedControl(
            options = listOf(stringResource(R.string.report_span_month), stringResource(R.string.report_span_year)),
            selectedIndex = ReportSpan.entries.indexOf(state.span),
            onSelect = { actions.onSpan(ReportSpan.entries[it]) },
        )
        PeriodStepper(state, actions)
    }
    if (state.reportKind == ReportKind.Business) {
        FwTextField(
            value = state.rateText,
            onValueChange = actions.onRate,
            label = stringResource(R.string.report_rate_label),
            placeholder = stringResource(R.string.report_rate_hint),
            unit = stringResource(R.string.report_rate_unit),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ReportPreview(state)
    val report = state.report
    Row(horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s)) {
        FwSecondaryButton(
            stringResource(R.string.report_share),
            { report?.let(actions.onShare) },
            Modifier.weight(1f),
            enabled = report != null,
        )
        FwSecondaryButton(
            stringResource(R.string.report_save_csv),
            actions.onSaveCsv,
            Modifier.weight(1f),
            enabled = report != null,
        )
    }
    // PDF reports are Pro (PRD F-13.3); the paywall arrives with F-19.
    if (!state.pdfAvailable) {
        Text(
            stringResource(R.string.report_pdf_pro),
            style = FwTheme.type.caption,
            color = FwTheme.colors.ink3,
        )
    }
}

@Composable
private fun PeriodStepper(state: ExportUiState, actions: ExportActions) {
    val locale = currentLocale()
    val label = if (state.span == ReportSpan.Month) {
        DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(state.month)
    } else {
        state.month.year.toString()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { actions.onStep(false) }, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(FwIcons.ChevronRight),
                stringResource(R.string.report_previous),
                tint = FwTheme.colors.ink,
                modifier = Modifier.rotate(HALF_TURN_DEGREES),
            )
        }
        Text(
            label,
            style = FwTheme.type.h3,
            color = FwTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(
            onClick = { actions.onStep(true) },
            enabled = state.canGoForward,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painterResource(FwIcons.ChevronRight),
                stringResource(R.string.report_next),
                tint = if (state.canGoForward) FwTheme.colors.ink else FwTheme.colors.ink3,
            )
        }
    }
}

@Composable
private fun ReportPreview(state: ExportUiState) {
    val resources = LocalResources.current
    val locale = currentLocale()
    val report = state.report
    FwCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        if (report == null) {
            Text(
                stringResource(
                    if (state.reportKind ==
                        ReportKind.Business
                    ) {
                        R.string.report_need_rate
                    } else {
                        R.string.report_pick_vehicle
                    },
                ),
                style = FwTheme.type.body,
                color = FwTheme.colors.ink2,
            )
        } else {
            val text = remember(report, state.vehicles, state.categories, locale) {
                ReportFormatter(resources, state.vehicles, state.categories, locale).text(report)
            }
            Text(text, style = FwTheme.type.bodyS, color = FwTheme.colors.ink)
        }
    }
}

private fun rangeLabel(range: DataRange): Int = when (range) {
    DataRange.All -> R.string.export_range_all
    DataRange.ThisMonth -> R.string.export_range_this_month
    DataRange.LastMonth -> R.string.export_range_last_month
    DataRange.LastThreeMonths -> R.string.export_range_three_months
    DataRange.ThisYear -> R.string.export_range_this_year
    DataRange.LastYear -> R.string.export_range_last_year
}

private fun kindLabel(kind: ReportKind): Int = when (kind) {
    ReportKind.Vehicle -> R.string.report_kind_vehicle
    ReportKind.Seller -> R.string.report_kind_seller
    ReportKind.Business -> R.string.report_kind_business
}

private const val ZIP_MIME = "application/zip"
private const val CSV_MIME = "text/csv"
private const val HALF_TURN_DEGREES = 180f

package app.fuelwise.feature.dataimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.data.repository.EntitlementRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FastagRepository
import app.fuelwise.core.data.repository.RecurrenceRepository
import app.fuelwise.core.data.repository.ReminderRepository
import app.fuelwise.core.data.repository.ReportPreferencesRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.export.CsvExport
import app.fuelwise.core.domain.export.CsvFile
import app.fuelwise.core.domain.export.ExportData
import app.fuelwise.core.domain.export.ExportFilter
import app.fuelwise.core.domain.export.ZipWriter
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.domain.report.BuildReports
import app.fuelwise.core.domain.report.ReportPeriod
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.format.MoneyInput
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Date presets for a CSV export (PRD F-13.1 "all data or a filtered range"). */
enum class DataRange { All, ThisMonth, LastMonth, LastThreeMonths, ThisYear, LastYear }

enum class ReportKind { Vehicle, Seller, Business }

/** Vehicle and business reports cover a month or a year (PRD F-13.3 "monthly/yearly"). */
enum class ReportSpan { Month, Year }

enum class ExportProblem { NothingToExport, WriteFailed }

data class ExportUiState(
    val loading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Vehicles to export; null means all of them. */
    val exportVehicleIds: Set<String>? = null,
    val range: DataRange = DataRange.All,
    val exporting: Boolean = false,
    val problem: ExportProblem? = null,
    val reportKind: ReportKind = ReportKind.Vehicle,
    /** Vehicle and seller reports need one; the business report takes null as every vehicle. */
    val reportVehicleId: String? = null,
    val span: ReportSpan = ReportSpan.Month,
    val month: YearMonth = YearMonth.from(LocalDate.EPOCH),
    val thisMonth: YearMonth = YearMonth.from(LocalDate.EPOCH),
    val rateText: String = "",
    val report: Report? = null,
    /** PDF reports are Pro (PRD F-13.3); free users get text and CSV. Billing arrives with F-19. */
    val pdfAvailable: Boolean = false,
) {
    val hasVehicles: Boolean get() = vehicles.isNotEmpty()
    val canGoForward: Boolean
        get() = if (span == ReportSpan.Month) month < thisMonth else month.year < thisMonth.year
}

sealed interface ExportEvent {
    /** Ask for a place to save the zip, through the system file picker. */
    data class PickExportFile(val fileName: String) : ExportEvent

    data class Exported(val files: Int) : ExportEvent

    data class PickReportFile(val fileName: String) : ExportEvent

    data class ReportSaved(val ok: Boolean) : ExportEvent
}

/** Export & reports (PRD F-13.1 CSV export, F-13.3 free text/CSV reports, F-07.9 business mileage). */
@HiltViewModel
@Suppress("LongParameterList") // One repository per exported entity.
class ExportViewModel @Inject constructor(
    private val vehicles: VehicleRepository,
    private val categories: CategoryRepository,
    private val entries: EntryRepository,
    private val trips: TripRepository,
    private val reminders: ReminderRepository,
    private val documents: DocumentRepository,
    private val fastag: FastagRepository,
    private val recurrences: RecurrenceRepository,
    private val reportPreferences: ReportPreferencesRepository,
    entitlements: EntitlementRepository,
    private val buildReports: BuildReports,
    private val clock: Clock,
    private val zoneId: ZoneId,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
    @param:Dispatcher(FwDispatcher.Default) private val compute: CoroutineDispatcher,
) : ViewModel() {
    private val today: LocalDate get() = Instant.ofEpochMilli(clock.millis()).atZone(zoneId).toLocalDate()

    private val local = MutableStateFlow(
        ExportUiState(month = YearMonth.from(today), thisMonth = YearMonth.from(today)),
    )

    /** The export waiting for the user to pick where to save it. */
    private var pendingFiles: List<CsvFile>? = null

    private data class Sources(
        val vehicles: List<Vehicle>,
        val categories: List<Category>,
        val refuels: List<Refuel>,
        val expenses: List<Expense>,
        val trips: List<Trip>,
        val documents: List<Document>,
    )

    private val sources = combine(
        vehicles.observeVehicles(includeArchived = true),
        categories.observeCategories(),
        entries.observeAllRefuels(),
        entries.observeAllExpenses(),
        combine(trips.observeTrips(), documents.observeDocuments(), ::Pair),
    ) { v, c, r, e, (t, d) -> Sources(v, c, r, e, t, d) }

    val state: StateFlow<ExportUiState> = combine(
        local,
        sources,
        reportPreferences.businessRatePerKm,
        entitlements.isPro,
    ) { s, src, rate, pro ->
        val vehicleId = s.reportVehicleId?.takeIf { id -> src.vehicles.any { it.id == id } }
            ?: if (s.reportKind == ReportKind.Business) null else src.vehicles.firstOrNull()?.id
        val rateText = s.rateText.ifEmpty { rate?.let(MoneyInput::plain).orEmpty() }
        val next = s.copy(
            loading = false,
            vehicles = src.vehicles,
            categories = src.categories,
            reportVehicleId = vehicleId,
            rateText = rateText,
            pdfAvailable = pro,
        )
        next.copy(report = report(next, src, rate))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    private val eventChannel = Channel<ExportEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private fun reportPeriod(s: ExportUiState): ReportPeriod =
        if (s.span == ReportSpan.Month) ReportPeriod.month(s.month) else ReportPeriod.year(Year.of(s.month.year))

    private fun report(s: ExportUiState, src: Sources, rate: Money?): Report? {
        val vehicle = src.vehicles.firstOrNull { it.id == s.reportVehicleId }
        return when (s.reportKind) {
            ReportKind.Vehicle -> vehicle?.let {
                Report.Vehicle(
                    buildReports.vehicle(
                        VehicleHistory(it, src.refuels, src.expenses),
                        src.categories,
                        reportPeriod(s),
                        zoneId,
                    ),
                )
            }

            ReportKind.Seller -> vehicle?.let {
                Report.Seller(
                    buildReports.seller(
                        VehicleHistory(it, src.refuels, src.expenses),
                        src.trips,
                        src.documents,
                        zoneId,
                    ),
                )
            }

            ReportKind.Business -> rate?.let {
                Report.Business(
                    buildReports.businessMileage(
                        src.trips,
                        s.reportVehicleId?.let(::setOf),
                        reportPeriod(s),
                        it,
                        zoneId,
                    ),
                )
            }
        }
    }

    // --- CSV export -----------------------------------------------------------------------------------------------

    fun toggleExportVehicle(id: String?) = local.update { s ->
        val all = state.value.vehicles.map { it.id }.toSet()
        val current = s.exportVehicleIds ?: all
        val next = when {
            id == null -> null
            id in current -> (current - id).takeIf { it.isNotEmpty() } ?: current
            else -> (current + id).takeIf { it != all }
        }
        s.copy(exportVehicleIds = next, problem = null)
    }

    fun setRange(range: DataRange) = local.update { it.copy(range = range, problem = null) }

    /** Builds the files first, so "nothing to export" shows before the file picker ever opens. */
    fun startExport() = viewModelScope.launch {
        val s = local.value
        local.update { it.copy(exporting = true, problem = null) }
        val bundle = runCatching {
            val data = snapshot()
            val names = data.categories.associate { it.id to ImportTexts.categoryName(context.resources, it) }
            withContext(compute) { CsvExport.export(data, filter(s), names, zoneId) }
        }.getOrNull()
        when {
            bundle == null -> local.update { it.copy(exporting = false, problem = ExportProblem.WriteFailed) }

            bundle.isEmpty -> local.update { it.copy(exporting = false, problem = ExportProblem.NothingToExport) }

            else -> {
                pendingFiles = bundle.files
                eventChannel.send(ExportEvent.PickExportFile(exportFileName(today)))
            }
        }
    }

    /** The picker closed: [uri] is where to write, or null when the user backed out. */
    fun writeExport(uri: Uri?) = viewModelScope.launch {
        val files = pendingFiles
        pendingFiles = null
        if (uri == null || files == null) {
            local.update { it.copy(exporting = false) }
            return@launch
        }
        val ok = runCatching {
            val bytes = withContext(compute) { ZipWriter.write(files, clock.millis()) }
            write(uri, bytes)
        }.getOrDefault(false)
        local.update { it.copy(exporting = false, problem = if (ok) null else ExportProblem.WriteFailed) }
        if (ok) eventChannel.send(ExportEvent.Exported(files.size))
    }

    private suspend fun snapshot() = ExportData(
        vehicles = vehicles.observeVehicles(includeArchived = true).first(),
        categories = categories.observeCategories().first(),
        refuels = entries.observeAllRefuels().first(),
        expenses = entries.observeAllExpenses().first(),
        trips = trips.observeTrips().first(),
        reminders = reminders.observeReminders().first(),
        documents = documents.observeDocuments().first(),
        fastagAccounts = fastag.observeAccounts().first(),
        fastagRecharges = fastag.observeRecharges().first(),
        recurrences = recurrences.observeRecurrences().first(),
    )

    private fun filter(s: ExportUiState): ExportFilter {
        val month = YearMonth.from(today)
        val (from, to) = when (s.range) {
            DataRange.All -> null to null
            DataRange.ThisMonth -> month.atDay(1) to null
            DataRange.LastMonth -> month.minusMonths(1).atDay(1) to month.atDay(1)
            DataRange.LastThreeMonths -> month.minusMonths(2).atDay(1) to null
            DataRange.ThisYear -> Year.from(today).atDay(1) to null
            DataRange.LastYear -> Year.from(today).minusYears(1).atDay(1) to Year.from(today).atDay(1)
        }
        fun ms(day: LocalDate?) = day?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli()
        return ExportFilter(s.exportVehicleIds, ms(from), ms(to))
    }

    // --- Reports --------------------------------------------------------------------------------------------------

    fun setReportKind(kind: ReportKind) = local.update {
        it.copy(reportKind = kind, reportVehicleId = state.value.reportVehicleId.takeIf { kind != ReportKind.Business })
    }

    fun setReportVehicle(id: String?) = local.update { it.copy(reportVehicleId = id) }

    fun setSpan(span: ReportSpan) = local.update { it.copy(span = span) }

    fun stepPeriod(forward: Boolean) = local.update { s ->
        val months = if (s.span == ReportSpan.Month) 1L else MONTHS_PER_YEAR
        val next = if (forward) s.month.plusMonths(months) else s.month.minusMonths(months)
        s.copy(month = minOf(next, s.thisMonth))
    }

    /** Typed rupees per km; saved as soon as it reads as money (PRD F-07.9 "rate, user-set"). */
    fun setRate(text: String) {
        local.update { it.copy(rateText = text) }
        viewModelScope.launch {
            val rate = MoneyInput.parse(text)?.takeIf { it.paise > 0 }
            reportPreferences.setBusinessRatePerKm(rate)
        }
    }

    fun saveReportCsv() = viewModelScope.launch {
        val report = state.value.report ?: return@launch
        eventChannel.send(ExportEvent.PickReportFile(reportFileName(report)))
    }

    fun writeReport(uri: Uri?) = viewModelScope.launch {
        val s = state.value
        val report = s.report ?: return@launch
        uri ?: return@launch
        val ok = runCatching {
            val csv = formatter(s).csv(report)
            write(uri, csv.toByteArray(Charsets.UTF_8))
        }.getOrDefault(false)
        eventChannel.send(ExportEvent.ReportSaved(ok))
    }

    private fun formatter(s: ExportUiState) = ReportFormatter(
        context.resources,
        s.vehicles,
        s.categories,
        context.resources.configuration.locales[0],
    )

    private suspend fun write(uri: Uri, bytes: ByteArray): Boolean = withContext(io) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
    }

    private fun reportFileName(report: Report): String {
        val s = state.value
        val period = if (s.span == ReportSpan.Month) s.month.toString() else s.month.year.toString()
        return when (report) {
            is Report.Vehicle -> "fuelwise-report-$period.csv"
            is Report.Seller -> "fuelwise-seller-report-$today.csv"
            is Report.Business -> "fuelwise-business-mileage-$period.csv"
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MONTHS_PER_YEAR = 12L

        fun exportFileName(today: LocalDate) = "fuelwise-export-$today.zip"
    }
}

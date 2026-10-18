package app.fuelwise.feature.dataimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.ImportRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.importing.ColumnMapping
import app.fuelwise.core.domain.importing.DateOrder
import app.fuelwise.core.domain.importing.EntryKind
import app.fuelwise.core.domain.importing.ImportContext
import app.fuelwise.core.domain.importing.ImportField
import app.fuelwise.core.domain.importing.ImportFile
import app.fuelwise.core.domain.importing.ImportPlan
import app.fuelwise.core.domain.importing.ImportPlanner
import app.fuelwise.core.domain.importing.ImportReader
import app.fuelwise.core.domain.importing.ImportTemplate
import app.fuelwise.core.domain.importing.ImportValues
import app.fuelwise.core.domain.importing.ImportVocabulary
import app.fuelwise.core.domain.importing.MappedTable
import app.fuelwise.core.domain.importing.RawTable
import app.fuelwise.core.model.ImportBatch
import app.fuelwise.core.model.ImportSource
import app.fuelwise.core.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

enum class ImportStep { Start, Reading, Mapping, Preview, Saving }

enum class ImportError { Unreadable, NoVehicles, Empty }

/** The column mapper for a spreadsheet the app doesn't know (PRD F-13.2 "generic CSV with a column mapper"). */
data class MapperState(
    val headers: List<String>,
    val samples: List<String?>,
    val mapping: ColumnMapping,
    val dateOrder: DateOrder,
)

/** The dry run: what would be saved, what's skipped and why. */
data class PreviewState(
    val source: ImportSource,
    val fileName: String?,
    val plan: ImportPlan,
    val askVehicle: Boolean,
    val vehicleId: String?,
)

data class ImportUiState(
    val step: ImportStep = ImportStep.Start,
    val batches: List<ImportBatch> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val mapper: MapperState? = null,
    val preview: PreviewState? = null,
    val error: ImportError? = null,
)

sealed interface ImportEvent {
    data class Imported(val batch: ImportBatch) : ImportEvent

    data object Undone : ImportEvent

    data class TemplateSaved(val ok: Boolean) : ImportEvent
}

/** Import data (PRD F-13.2): one entry point for the template, Fuelio and any other spreadsheet. */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val imports: ImportRepository,
    private val vehicles: VehicleRepository,
    private val categories: CategoryRepository,
    private val entries: EntryRepository,
    private val userData: UserDataRepository,
    private val zoneId: ZoneId,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
    @param:Dispatcher(FwDispatcher.Default) private val compute: CoroutineDispatcher,
) : ViewModel() {
    private val local = MutableStateFlow(ImportUiState())

    /** The file being imported, kept between the mapper and the preview. */
    private var pending: Pending? = null

    private class Pending(
        val file: ImportFile,
        val fileName: String?,
        val vocabulary: ImportVocabulary,
        var tables: List<MappedTable> = file.tables,
    )

    val state: StateFlow<ImportUiState> = combine(
        local,
        imports.observeBatches(),
        vehicles.observeVehicles(),
    ) { s, batches, vehicles -> s.copy(batches = batches, vehicles = vehicles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImportUiState())

    private val eventChannel = Channel<ImportEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val templateFileName: String get() = TEMPLATE_FILE

    fun pickFile(uri: Uri) = viewModelScope.launch {
        local.update { it.copy(step = ImportStep.Reading, error = null) }
        val error = runCatching { open(uri) }.getOrElse { ImportError.Unreadable }
        if (error != null) local.update { it.copy(step = ImportStep.Start, error = error) }
    }

    /** Reads and recognises the file, then shows the mapper or the preview. Returns an error to show, if any. */
    private suspend fun open(uri: Uri): ImportError? {
        if (vehicles.observeVehicles().first().isEmpty()) return ImportError.NoVehicles
        val (bytes, name) = withContext(io) { read(uri) }
        val vocabulary = ImportTexts.vocabulary(context, categories.observeCategories().first())
        val file = withContext(compute) { ImportReader.read(bytes, vocabulary) }
        val table = file.unmapped
        pending = Pending(file, name, vocabulary)
        return when {
            table != null -> if (table.rows.none { r ->
                    r.any { !it.isBlank }
                }
            ) {
                ImportError.Empty
            } else {
                showMapper(table, vocabulary)
            }

            file.tables.none { t -> t.table.rows.isNotEmpty() } && file.problems.isEmpty() -> ImportError.Empty

            else -> {
                plan(vehicleId = null)
                null
            }
        }
    }

    private suspend fun showMapper(table: RawTable, vocabulary: ImportVocabulary): ImportError? {
        val signature = ColumnMapping.signature(table.headers)
        val mapping = imports.savedMapping(signature)
            ?: ColumnMapping.suggest(table.headers, ColumnMapping.guessKind(table.headers, vocabulary), vocabulary)
        val dates = mapping[ImportField.Date]?.let { c -> table.rows.mapNotNull { it.getOrNull(c) } }.orEmpty()
        local.update {
            it.copy(
                step = ImportStep.Mapping,
                mapper = MapperState(
                    headers = table.headers,
                    samples = table.headers.indices.map { c -> table.samples(c, 1).firstOrNull() },
                    mapping = mapping,
                    dateOrder = ImportValues.detectDateOrder(dates) ?: DateOrder.DayMonthYear,
                ),
            )
        }
        return null
    }

    private fun read(uri: Uri): Pair<ByteArray, String?> {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val bytes = requireNotNull(resolver.openInputStream(uri)) { "Can't open the file" }.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                require(out.size() <= MAX_FILE_BYTES) { "File is too large" }
            }
            out.toByteArray()
        }
        return bytes to (name ?: uri.lastPathSegment)?.substringAfterLast('/')?.substringAfterLast('\\')
    }

    fun setKind(kind: EntryKind) = updateMapper { m ->
        val vocabulary = pending?.vocabulary ?: return@updateMapper m
        m.copy(mapping = ColumnMapping.suggest(m.headers, kind, vocabulary))
    }

    fun setColumn(field: ImportField, column: Int?) = updateMapper { m ->
        val columns = m.mapping.columns.filterValues { it != column }.toMutableMap()
        if (column == null) columns.remove(field) else columns[field] = column
        m.copy(mapping = m.mapping.copy(columns = columns))
    }

    fun setDateOrder(order: DateOrder) = updateMapper { it.copy(dateOrder = order) }

    private fun updateMapper(transform: (MapperState) -> MapperState) =
        local.update { s -> s.mapper?.let { s.copy(mapper = transform(it)) } ?: s }

    fun confirmMapping() = viewModelScope.launch {
        val mapper = local.value.mapper ?: return@launch
        val p = pending ?: return@launch
        val table = p.file.unmapped ?: return@launch
        if (!mapper.mapping.isComplete) return@launch
        imports.saveMapping(ColumnMapping.signature(table.headers), mapper.mapping)
        p.tables = listOf(MappedTable(table, mapper.mapping))
        plan(vehicleId = local.value.preview?.vehicleId)
    }

    fun setVehicle(id: String) = viewModelScope.launch { plan(vehicleId = id) }

    /** Builds the dry run. The vehicle for rows without one: [vehicleId], else the file's own, else the selected. */
    private suspend fun plan(vehicleId: String?) {
        val p = pending ?: return
        val all = vehicles.observeVehicles().first()
        val hint = p.file.vehicleHint?.let(ImportVocabulary::normalize)
        val chosen = vehicleId
            ?: all.firstOrNull { hint != null && ImportVocabulary.normalize(it.name) == hint }?.id
            ?: userData.userData.first().selectedVehicleId?.takeIf { id -> all.any { it.id == id } }
            ?: all.firstOrNull()?.id
        val context = ImportContext(
            vehicles = all,
            defaultVehicleId = chosen,
            existingRefuels = entries.observeAllRefuels().first(),
            existingExpenses = entries.observeAllExpenses().first(),
            vocabulary = p.vocabulary,
            zone = zoneId,
            dateOrder = local.value.mapper?.dateOrder ?: DateOrder.DayMonthYear,
        )
        val plan = withContext(compute) { ImportPlanner.plan(p.tables, context, p.file.problems) }
        val preview = PreviewState(
            source = p.file.source,
            fileName = p.fileName,
            plan = plan,
            askVehicle = ImportPlanner.usesDefaultVehicle(p.tables),
            vehicleId = chosen,
        )
        local.update { it.copy(step = ImportStep.Preview, preview = preview) }
    }

    fun confirmImport() = viewModelScope.launch {
        val preview = local.value.preview ?: return@launch
        if (preview.plan.entryCount == 0) return@launch
        local.update { it.copy(step = ImportStep.Saving) }
        val batch = imports.commit(preview.plan, preview.source, preview.fileName)
        reset()
        eventChannel.send(ImportEvent.Imported(batch))
    }

    /** Back from the preview returns to the mapper when there was one; otherwise to the start. */
    fun back() {
        val s = local.value
        if (s.step == ImportStep.Preview && s.mapper != null) {
            local.update { it.copy(step = ImportStep.Mapping, preview = null) }
        } else {
            reset()
        }
    }

    private fun reset() {
        pending = null
        local.update { ImportUiState() }
    }

    fun undo(batchId: String) = viewModelScope.launch {
        imports.undo(batchId)
        eventChannel.send(ImportEvent.Undone)
    }

    fun saveTemplate(uri: Uri) = viewModelScope.launch {
        val ok = runCatching {
            val (labels, lists) = ImportTexts.template(
                context.resources,
                vehicles.observeVehicles().first(),
                categories.observeCategories().first(),
            )
            val bytes = withContext(compute) { ImportTemplate.write(labels, lists) }
            withContext(io) {
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "No output" }.use { it.write(bytes) }
            }
        }.isSuccess
        eventChannel.send(ImportEvent.TemplateSaved(ok))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        const val BUFFER_BYTES = 8 * 1024
        const val TEMPLATE_FILE = "fuelwise-template.xlsx"
    }
}

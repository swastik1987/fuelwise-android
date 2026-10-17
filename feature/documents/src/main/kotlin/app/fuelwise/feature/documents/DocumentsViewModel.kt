package app.fuelwise.feature.documents

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.InsuranceDetails
import app.fuelwise.core.model.PolicyType
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.format.MoneyInput
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DocumentsUiState(
    val loading: Boolean = true,
    val documents: List<Document> = emptyList(),
    val vehicles: Map<String, Vehicle> = emptyMap(),
    val nowUtcMs: Long = 0,
)

/** Documents vault list (design.md screen 24). */
@HiltViewModel
class DocumentsViewModel @Inject constructor(documents: DocumentRepository, vehicles: VehicleRepository, clock: Clock) :
    ViewModel() {
    val state: StateFlow<DocumentsUiState> = combine(
        documents.observeDocuments(),
        vehicles.observeVehicles(includeArchived = true),
    ) {
            docs,
            list,
        ->
        DocumentsUiState(false, docs, list.associateBy { it.id }, clock.millis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DocumentsUiState())
}

/** A decrypted attachment, ready to draw: a photo, or the first page of a PDF. */
data class AttachmentPreview(val attachment: Attachment, val image: ImageBitmap?)

/**
 * Decrypts and decodes attachments off the main thread. Plaintext never touches disk, except a PDF's moment in the
 * cache while its first page renders.
 */
class AttachmentPreviews @Inject constructor(
    private val documents: DocumentRepository,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(ownerId: String, maxPx: Int): Flow<List<AttachmentPreview>> =
        documents.observeAttachments(ownerId).map { list ->
            list.map { AttachmentPreview(it, runCatching { decode(it, maxPx) }.getOrNull()) }
        }

    private suspend fun decode(attachment: Attachment, maxPx: Int): ImageBitmap? = withContext(io) {
        val bytes = documents.readAttachment(attachment)
        if (attachment.mimeType == PDF) {
            val temp = File.createTempFile("doc", ".pdf", context.cacheDir)
            try {
                temp.writeBytes(bytes)
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val scale = maxPx.toFloat() / maxOf(page.width, page.height)
                            val bitmap = createBitmap((page.width * scale).toInt(), (page.height * scale).toInt())
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap.asImageBitmap()
                        }
                    }
                }
            } finally {
                temp.delete()
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                },
            )?.asImageBitmap()
        }
    }

    companion object {
        const val PDF = "application/pdf"
    }
}

data class DocumentForm(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val id: String = "",
    val vehicles: List<Vehicle> = emptyList(),
    val vehicleId: String? = null,
    val type: DocumentType = DocumentType.Insurance,
    val number: String = "",
    val issuer: String = "",
    val issueDateUtcMs: Long? = null,
    val expiryDateUtcMs: Long? = null,
    val notes: String = "",
    val insurer: String = "",
    val policyType: PolicyType? = null,
    val idv: String = "",
    val ncb: String = "",
    val addOns: String = "",
    val remindOnExpiry: Boolean = true,
    val nowUtcMs: Long = 0,
)

sealed interface DocumentEditorEvent {
    data object Closed : DocumentEditorEvent

    data object AttachFailed : DocumentEditorEvent
}

/** Add / edit a document (design.md screen 24, add flow). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    vehicles: VehicleRepository,
    userData: UserDataRepository,
    ids: IdGenerator,
    previews: AttachmentPreviews,
    clock: Clock,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<DocumentEditorRoute>()

    // A new document gets its id up front so photos can be attached before the first save.
    private val documentId = route.documentId ?: ids.newId()
    private val addedThisSession = mutableListOf<String>()

    private val _state = MutableStateFlow(DocumentForm())
    val state: StateFlow<DocumentForm> = _state.asStateFlow()

    val attachments: StateFlow<List<AttachmentPreview>> = flowOf(documentId)
        .flatMapLatest { previews.observe(it, THUMB_PX) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val eventChannel = Channel<DocumentEditorEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val list = vehicles.observeVehicles().first()
            val existing = route.documentId?.let { documents.observeDocument(it).first() }
            val selected = userData.userData.first().selectedVehicleId
            _state.value = if (existing == null) {
                DocumentForm(
                    loading = false,
                    id = documentId,
                    vehicles = list,
                    vehicleId = list.firstOrNull { it.id == selected }?.id ?: list.firstOrNull()?.id,
                    type =
                        route.type?.let { t -> DocumentType.entries.firstOrNull { it.name == t } }
                            ?: DocumentType.Insurance,
                    nowUtcMs = clock.millis(),
                )
            } else {
                DocumentForm(
                    loading = false,
                    isEdit = true,
                    id = existing.id,
                    vehicles = list,
                    vehicleId = existing.vehicleId,
                    type = existing.type,
                    number = existing.number.orEmpty(),
                    issuer = existing.issuer.orEmpty(),
                    issueDateUtcMs = existing.issueDateUtcMs,
                    expiryDateUtcMs = existing.expiryDateUtcMs,
                    notes = existing.notes.orEmpty(),
                    insurer = existing.insurance?.insurer.orEmpty(),
                    policyType = existing.insurance?.policyType,
                    idv = existing.insurance?.idv?.let { MoneyInput.plain(it) }.orEmpty(),
                    ncb = existing.insurance?.ncbPercent?.toString().orEmpty(),
                    addOns = existing.insurance?.addOns.orEmpty(),
                    remindOnExpiry = existing.remindOnExpiry,
                    nowUtcMs = clock.millis(),
                )
            }
        }
    }

    fun update(transform: (DocumentForm) -> DocumentForm) = _state.update(transform)

    fun attach(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val (bytes, mime) = withContext(io) {
                    val resolver = context.contentResolver
                    val type = resolver.getType(uri) ?: "image/jpeg"
                    (resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unreadable")) to type
                }
                require(bytes.size <= MAX_BYTES) { "Too large" }
                addedThisSession += documents.addAttachment(documentId, Attachment.OwnerType.Document, bytes, mime)
            }.isSuccess
            if (!ok) eventChannel.send(DocumentEditorEvent.AttachFailed)
        }
    }

    fun removeAttachment(attachment: Attachment) {
        viewModelScope.launch { documents.removeAttachment(attachment) }
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            documents.save(
                Document(
                    id = documentId,
                    vehicleId = s.vehicleId,
                    type = s.type,
                    number = s.number,
                    issuer = s.issuer,
                    issueDateUtcMs = s.issueDateUtcMs,
                    expiryDateUtcMs = s.expiryDateUtcMs,
                    notes = s.notes,
                    insurance = if (s.type == DocumentType.Insurance) {
                        InsuranceDetails(
                            insurer = s.insurer,
                            policyType = s.policyType,
                            idv = MoneyInput.parse(s.idv),
                            ncbPercent = s.ncb.toIntOrNull()?.coerceIn(0, MAX_NCB),
                            addOns = s.addOns,
                        )
                    } else {
                        null
                    },
                    remindOnExpiry = s.remindOnExpiry,
                ),
            )
            addedThisSession.clear()
            eventChannel.send(DocumentEditorEvent.Closed)
        }
    }

    /** Leaving a new document without saving removes the photos attached to it. */
    fun discard() {
        viewModelScope.launch {
            if (!_state.value.isEdit) {
                documents.observeAttachments(documentId).first().filter {
                    it.id in addedThisSession
                }.forEach { documents.removeAttachment(it) }
            }
            eventChannel.send(DocumentEditorEvent.Closed)
        }
    }

    fun delete() {
        viewModelScope.launch {
            documents.delete(documentId)
            eventChannel.send(DocumentEditorEvent.Closed)
        }
    }

    private companion object {
        const val THUMB_PX = 320
        const val MAX_BYTES = 15 * 1024 * 1024
        const val MAX_NCB = 65
    }
}

data class DocumentViewState(val document: Document? = null, val vehicle: Vehicle? = null, val nowUtcMs: Long = 0)

/** Quick view (design.md screen 24): large card for a checkpoint, plus the photos. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    documents: DocumentRepository,
    vehicles: VehicleRepository,
    previews: AttachmentPreviews,
    clock: Clock,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<DocumentViewRoute>().documentId

    val state: StateFlow<DocumentViewState> = documents.observeDocument(id).flatMapLatest { doc ->
        val vehicleFlow = doc?.vehicleId?.let { vehicles.observeVehicle(it) } ?: flowOf(null)
        vehicleFlow.map { DocumentViewState(doc, it, clock.millis()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DocumentViewState())

    val attachments: StateFlow<List<AttachmentPreview>> = previews.observe(id, FULL_PX)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private companion object {
        const val FULL_PX = 1600
    }
}

private const val STOP_TIMEOUT_MS = 5_000L

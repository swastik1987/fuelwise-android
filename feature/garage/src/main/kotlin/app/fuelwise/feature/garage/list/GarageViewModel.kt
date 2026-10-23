package app.fuelwise.feature.garage.list

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.BackupSettingsRepository
import app.fuelwise.core.data.repository.DeletedVehicle
import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FastagRepository
import app.fuelwise.core.data.repository.ObserveReminderStatuses
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.recurrence.balance
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.sum
import app.fuelwise.feature.garage.PENDING_DELETE_VEHICLE_ID
import app.fuelwise.feature.garage.photo.VehiclePhotoLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GarageUiState {
    data object Loading : GarageUiState

    data class Ready(
        val active: List<Vehicle>,
        val archived: List<Vehicle>,
        /** Utility rows (design.md screen 21). */
        val remindersDue: Int = 0,
        val documentsExpiring: Int = 0,
        val fastagEstimate: Money? = null,
        val backup: BackupHealth = BackupHealth.Off,
        /** Card photos (design.md screen 21), by vehicle id. */
        val photos: Map<String, ImageBitmap> = emptyMap(),
    ) : GarageUiState
}

/** The status dot on the Backup & sync row. */
enum class BackupHealth { Ok, Error, Off }

sealed interface GarageEvent {
    data class VehicleDeleted(val name: String, val token: DeletedVehicle) : GarageEvent
}

@HiltViewModel
class GarageViewModel @Inject constructor(
    private val vehicles: VehicleRepository,
    private val savedStateHandle: SavedStateHandle,
    statuses: ObserveReminderStatuses,
    documents: DocumentRepository,
    fastag: FastagRepository,
    entries: EntryRepository,
    backupSettings: BackupSettingsRepository,
    photos: VehiclePhotoLoader,
    clock: Clock,
) : ViewModel() {
    private val ready = combine(
        vehicles.observeVehicles(includeArchived = true),
        statuses(),
        documents.observeDocuments(),
        combine(fastag.observeAccounts(), fastag.observeRecharges(), entries.observeAllExpenses()) { a, r, e ->
            a.takeIf { it.isNotEmpty() }?.map { it.balance(r, e).estimate }?.sum()
        },
        backupSettings.settings,
    ) { all, reminders, docs, fastagTotal, backup ->
        val soon = clock.millis() + EXPIRING_MS
        GarageUiState.Ready(
            active = all.filterNot { it.isArchived },
            archived = all.filter { it.isArchived },
            remindersDue = reminders.count { it.urgency == Urgency.Overdue || it.urgency == Urgency.Due },
            documentsExpiring = docs.count { d -> d.expiryDateUtcMs?.let { it <= soon } == true },
            fastagEstimate = fastagTotal,
            backup = when {
                backup.lastError != null -> BackupHealth.Error
                backup.lastSuccessUtcMs != null -> BackupHealth.Ok
                else -> BackupHealth.Off
            },
        )
    }

    val uiState: StateFlow<GarageUiState> =
        combine(ready, photos.observe(CARD_PX).onStart { emit(emptyMap()) }) { s, p -> s.copy(photos = p) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GarageUiState.Loading)

    private val eventChannel = Channel<GarageEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(PENDING_DELETE_VEHICLE_ID, null).filterNotNull().collect { id ->
                savedStateHandle[PENDING_DELETE_VEHICLE_ID] = null
                val name = vehicles.observeVehicle(id).first()?.name ?: return@collect
                eventChannel.send(GarageEvent.VehicleDeleted(name, vehicles.delete(id)))
            }
        }
    }

    fun undoDelete(token: DeletedVehicle) {
        viewModelScope.launch { vehicles.undoDelete(token) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val CARD_PX = 720
        const val EXPIRING_MS = 30L * 24 * 60 * 60 * 1000
    }
}

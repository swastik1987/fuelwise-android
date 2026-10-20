package app.fuelwise.feature.insights

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.insights.ComputeRecap
import app.fuelwise.core.domain.insights.Recap
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecapUiState(
    val recap: Recap,
    val vehicle: Vehicle,
    val showVehicleName: Boolean,
    /** The vehicle's display units (PRD F-18) for the best-tank figure. */
    val units: DisplayUnits = DisplayUnits.INDIA,
)

sealed interface RecapEvent {
    data class Share(val uri: Uri) : RecapEvent

    data object Saved : RecapEvent

    data object Failed : RecapEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    vehicles: VehicleRepository,
    entries: EntryRepository,
    categories: CategoryRepository,
    userData: UserDataRepository,
    computeRecap: ComputeRecap,
    zoneId: ZoneId,
    clock: Clock,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : ViewModel() {
    private val month: YearMonth = savedStateHandle.toRoute<RecapRoute>().month?.let(YearMonth::parse)
        ?: YearMonth.now(clock.withZone(zoneId)).minusMonths(1)

    /** Off by default: no personal data on the card (PRD F-17). */
    private val showVehicleName = MutableStateFlow(false)

    val state: StateFlow<RecapUiState?> = combine(
        vehicles.observeVehicles(),
        userData.userData,
        entries.observeAllRefuels(),
        entries.observeAllExpenses(),
        categories.observeCategories(),
    ) { list, data, refuels, expenses, cats ->
        val vehicle = list.firstOrNull { it.id == data.selectedVehicleId } ?: list.firstOrNull()
        vehicle?.let { v ->
            val history = VehicleHistory(
                v,
                refuels.filter { it.vehicleId == v.id },
                expenses.filter {
                    it.vehicleId ==
                        v.id
                },
            )
            Triple(v, computeRecap(history, cats, month, zoneId), data.units.forVehicle(v.id))
        }
    }.combine(showVehicleName) { triple, show ->
        triple?.let { (vehicle, recap, units) -> RecapUiState(recap, vehicle, show, units) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val eventChannel = Channel<RecapEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun setShowVehicleName(show: Boolean) = showVehicleName.update { show }

    /** Writes the card to the app cache and hands back a content:// URI for the share sheet. */
    fun share(bitmap: Bitmap) {
        viewModelScope.launch {
            val uri = runCatching {
                withContext(io) {
                    val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                    val file = File(dir, "fuelwise-recap-$month.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
                }
            }.getOrNull()
            eventChannel.send(if (uri != null) RecapEvent.Share(uri) else RecapEvent.Failed)
        }
    }

    /** Writes the card to a document the user picked (no storage permission needed). */
    fun save(bitmap: Bitmap, target: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(io) {
                    context.contentResolver.openOutputStream(target)?.use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, it)
                    }
                        ?: error("No output stream")
                }
            }.isSuccess
            eventChannel.send(if (ok) RecapEvent.Saved else RecapEvent.Failed)
        }
    }

    val fileName: String get() = "fuelwise-recap-$month.png"

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SHARE_DIR = "recaps"
        const val AUTHORITY_SUFFIX = ".recap"
        const val QUALITY = 100
    }
}

package app.fuelwise.feature.garage.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import app.fuelwise.core.data.repository.CarLink
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehiclePhotoRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.vehicle.ValidateVehicleDraft
import app.fuelwise.core.domain.vehicle.VehicleDraftError
import app.fuelwise.core.location.CarLinker
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.NumberInput
import app.fuelwise.core.ui.photo.PhotoImages
import app.fuelwise.feature.garage.VehicleEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VehicleEditorEvent {
    data class Saved(val vehicleId: String, val onboarding: Boolean) : VehicleEditorEvent
}

@Suppress("TooManyFunctions") // One handler per form field, as the screen needs.
@HiltViewModel
class VehicleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val userData: UserDataRepository,
    private val validate: ValidateVehicleDraft,
    private val autoTripSettings: AutoTripSettingsRepository,
    private val carLinker: CarLinker,
    private val photos: VehiclePhotoRepository,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<VehicleEditorRoute>()
    private var editing: Vehicle? = null
    private var attemptedSave = false

    /** A picked photo waiting for save, or [PhotoChange.Remove]. */
    private var photoChange: PhotoChange? = null

    private val _state = MutableStateFlow(
        VehicleFormState(
            isLoading = route.vehicleId != null,
            isEditing = route.vehicleId != null,
            onboarding = route.onboarding,
        ),
    )
    val state: StateFlow<VehicleFormState> = _state.asStateFlow()

    private val eventChannel = Channel<VehicleEditorEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        route.vehicleId?.let { id ->
            viewModelScope.launch { load(id) }
            viewModelScope.launch {
                autoTripSettings.settings.collect { s ->
                    val link = s.carLinks[id]
                    _state.update {
                        it.copy(carBluetoothOffered = s.available, carLinked = link != null, carLinkName = link?.name)
                    }
                }
            }
        }
    }

    /** The car picked in the system chooser now starts this vehicle's automatic trips. */
    fun linkCar(link: CarLink) {
        val id = route.vehicleId ?: return
        viewModelScope.launch { carLinker.linkCar(id, link) }
    }

    fun unlinkCar() {
        val id = route.vehicleId ?: return
        viewModelScope.launch { carLinker.unlinkCar(id) }
    }

    private suspend fun load(id: String) {
        val vehicle = vehicles.observeVehicle(id).first() ?: return
        editing = vehicle
        val photo = photos.observePhotos().first()[id]?.let { p ->
            withContext(io) { runCatching { PhotoImages.decode(photos.read(p), PREVIEW_PX) }.getOrNull() }
        }
        _state.update {
            it.copy(
                isLoading = false,
                name = vehicle.name,
                type = vehicle.type,
                fuelSetup = FuelSetup.entries.firstOrNull { s -> s.tanks == vehicle.tanks.map { t -> t.energyType } }
                    ?: FuelSetup.Petrol,
                odometer = IndianNumberFormat.decimal(vehicle.startOdometerKm, 0),
                capacities = vehicle.tanks.map { t -> t.capacity?.let(::plain).orEmpty() },
                registration = vehicle.registrationNumber.orEmpty(),
                emissionNorm = vehicle.emissionNorm,
                claimed = vehicle.claimedKmPerUnit?.let(::plain).orEmpty(),
                details = vehicle.detailsText(),
                purchaseDate = vehicle.purchaseDate,
                isHybrid = vehicle.isHybrid,
                defaultGrade = vehicle.tanks.firstNotNullOfOrNull { t -> t.defaultGrade },
                photo = photo,
                moreExpanded = true,
            )
        }
    }

    fun onNameChange(value: String) = edit { copy(name = value) }

    fun onTypeChange(value: VehicleType) = edit { copy(type = value) }

    fun onFuelSetupChange(value: FuelSetup) = edit {
        if (isEditing) {
            this
        } else {
            copy(
                fuelSetup = value,
                capacities = List(value.tanks.size) {
                    capacities.getOrElse(it) { "" }
                },
            )
        }
    }

    fun onOdometerChange(value: String) = edit {
        if (isEditing) this else copy(odometer = NumberInput.sanitize(value, 1))
    }

    fun onCapacityChange(index: Int, value: String) = edit {
        copy(capacities = capacities.toMutableList().also { it[index] = NumberInput.sanitize(value, 1) })
    }

    fun onRegistrationChange(value: String) = edit { copy(registration = value.uppercase().take(MAX_REG_INPUT)) }

    fun onEmissionNormChange(value: EmissionNorm) = edit {
        copy(
            emissionNorm = if (emissionNorm ==
                value
            ) {
                null
            } else {
                value
            },
        )
    }

    fun onClaimedChange(value: String) = edit { copy(claimed = NumberInput.sanitize(value, 1)) }

    fun onDetailChange(field: VehicleDetail, value: String) = edit {
        copy(details = details + (field to field.sanitize(value)))
    }

    fun onPurchaseDateChange(value: LocalDate?) = edit { copy(purchaseDate = value) }

    fun onHybridChange(value: Boolean) = edit { copy(isHybrid = value) }

    /** Tapping the chosen grade again clears it. */
    fun onDefaultGradeChange(value: FuelGrade) = edit {
        copy(
            defaultGrade = if (defaultGrade ==
                value
            ) {
                null
            } else {
                value
            },
        )
    }

    /** A photo from the system picker (no storage permission), shrunk to a JPEG for the screen. */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val shrunk = withContext(io) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let(PhotoImages::shrinkToJpeg)
                }.getOrNull()
            }
            val preview = shrunk?.let { withContext(io) { PhotoImages.decode(it, PREVIEW_PX) } }
            if (shrunk == null || preview == null) {
                _state.update { it.copy(photoFailed = true) }
            } else {
                photoChange = PhotoChange.Set(shrunk)
                _state.update { it.copy(photo = preview, photoFailed = false) }
            }
        }
    }

    fun onPhotoRemoved() {
        photoChange = PhotoChange.Remove
        _state.update { it.copy(photo = null, photoFailed = false) }
    }

    fun onToggleMore() = edit { copy(moreExpanded = !moreExpanded) }

    fun save() {
        val form = _state.value
        if (form.isSaving) return
        attemptedSave = true
        val draft = form.toDraft()
        val errors = validate(draft)
        _state.update {
            it.copy(errors = errors, moreExpanded = it.moreExpanded || errors.any { e -> e in MORE_ERRORS })
        }
        if (errors.isNotEmpty()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val existing = editing
            val id = if (existing == null) {
                vehicles.add(draft).also { userData.setSelectedVehicle(it) }
            } else {
                vehicles.update(existing.applyDraft(draft))
                existing.id
            }
            when (val change = photoChange) {
                is PhotoChange.Set -> photos.setPhoto(id, change.jpeg)
                PhotoChange.Remove -> photos.removePhoto(id)
                null -> Unit
            }
            eventChannel.send(VehicleEditorEvent.Saved(id, route.onboarding))
        }
    }

    private fun edit(transform: VehicleFormState.() -> VehicleFormState) {
        _state.update { current ->
            val next = current.transform()
            if (attemptedSave) next.copy(errors = validate(next.toDraft())) else next
        }
    }

    private companion object {
        const val MAX_REG_INPUT = 16
        const val PREVIEW_PX = 480
        val MORE_ERRORS = setOf(
            VehicleDraftError.CapacityNotPositive,
            VehicleDraftError.RegistrationInvalid,
            VehicleDraftError.ClaimedNotPositive,
            VehicleDraftError.YearInvalid,
            VehicleDraftError.PurchaseInFuture,
            VehicleDraftError.PurchaseOdometerInvalid,
            VehicleDraftError.PurchasePriceNotPositive,
        )

        fun plain(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
}

private sealed interface PhotoChange {
    class Set(val jpeg: ByteArray) : PhotoChange

    data object Remove : PhotoChange
}

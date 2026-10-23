package app.fuelwise.feature.garage.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.AutoTripSettingsRepository
import app.fuelwise.core.data.repository.CarLink
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.vehicle.RegistrationNumber
import app.fuelwise.core.domain.vehicle.ValidateVehicleDraft
import app.fuelwise.core.domain.vehicle.VehicleDraft
import app.fuelwise.core.domain.vehicle.VehicleDraftError
import app.fuelwise.core.location.CarLinker
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.NumberInput
import app.fuelwise.feature.garage.VehicleEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Form state for design.md screen 2. Numbers are kept as text while typing and parsed on save.
 * In edit mode the fuel setup and starting odometer are fixed: changing them would rewrite history.
 */
data class VehicleFormState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val onboarding: Boolean = false,
    val name: String = "",
    val type: VehicleType = VehicleType.Car,
    val fuelSetup: FuelSetup = FuelSetup.Petrol,
    val odometer: String = "",
    val capacities: List<String> = listOf(""),
    val registration: String = "",
    val emissionNorm: EmissionNorm? = null,
    val claimed: String = "",
    val moreExpanded: Boolean = false,
    /** Shown only after the first save attempt, so an empty form doesn't open covered in red. */
    val errors: Set<VehicleDraftError> = emptySet(),
    val isSaving: Boolean = false,
    /**
     * Car Bluetooth for automatic trips (design.md screen 22, PRD F-21.3): offered when editing a saved vehicle
     * while automatic trips are available. [carLinkName] is the linked car, or null.
     */
    val carBluetoothOffered: Boolean = false,
    val carLinked: Boolean = false,
    val carLinkName: String? = null,
) {
    /** Two-wheelers have no car kit to link (design.md: "Two-wheelers can skip this"). */
    val showCarBluetooth: Boolean
        get() = carBluetoothOffered && type != VehicleType.Scooter && type != VehicleType.Motorcycle
}

sealed interface VehicleEditorEvent {
    data class Saved(val vehicleId: String, val onboarding: Boolean) : VehicleEditorEvent
}

@HiltViewModel
class VehicleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val userData: UserDataRepository,
    private val validate: ValidateVehicleDraft,
    private val autoTripSettings: AutoTripSettingsRepository,
    private val carLinker: CarLinker,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<VehicleEditorRoute>()
    private var editing: Vehicle? = null
    private var attemptedSave = false

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
            eventChannel.send(VehicleEditorEvent.Saved(id, route.onboarding))
        }
    }

    private fun edit(transform: VehicleFormState.() -> VehicleFormState) {
        _state.update { current ->
            val next = current.transform()
            if (attemptedSave) next.copy(errors = validate(next.toDraft())) else next
        }
    }

    private fun VehicleFormState.toDraft() = VehicleDraft(
        name = name,
        type = type,
        fuelSetup = fuelSetup,
        odometerKm = NumberInput.parseDecimal(odometer),
        tankCapacities = capacities.map(NumberInput::parseDecimal),
        registrationNumber = registration,
        emissionNorm = emissionNorm,
        claimedKmPerUnit = NumberInput.parseDecimal(claimed),
    )

    private fun Vehicle.applyDraft(draft: VehicleDraft) = copy(
        name = draft.name.trim(),
        type = draft.type,
        registrationNumber = draft.registrationNumber.takeIf { it.isNotBlank() }?.let(RegistrationNumber::format),
        emissionNorm = draft.emissionNorm,
        claimedKmPerUnit = draft.claimedKmPerUnit,
        tanks = tanks.mapIndexed { i, tank -> tank.copy(capacity = draft.tankCapacities.getOrNull(i)) },
    )

    private companion object {
        const val MAX_REG_INPUT = 16
        val MORE_ERRORS = setOf(
            VehicleDraftError.CapacityNotPositive,
            VehicleDraftError.RegistrationInvalid,
            VehicleDraftError.ClaimedNotPositive,
        )

        fun plain(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
}

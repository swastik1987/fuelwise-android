package app.fuelwise.feature.garage.editor

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.repository.CarLink
import app.fuelwise.core.domain.vehicle.ValidateVehicleDraft
import app.fuelwise.core.domain.vehicle.VehicleDraftError
import app.fuelwise.core.location.CarLinker
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.VehicleType
import app.fuelwise.core.testing.FakeAutoTripSettingsRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VehicleEditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari))
    private val userData = FakeUserDataRepository()

    private fun viewModel(vehicleId: String? = null, onboarding: Boolean = false) = VehicleEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("vehicleId" to vehicleId, "onboarding" to onboarding)),
        vehicles = vehicles,
        userData = userData,
        validate = ValidateVehicleDraft(Clock.fixed(Instant.parse("2026-10-23T04:30:00Z"), ZoneOffset.UTC)),
        autoTripSettings = autoTrips,
        carLinker = linker,
    )

    private val autoTrips = FakeAutoTripSettingsRepository()
    private val linker = object : CarLinker {
        override suspend fun linkCar(vehicleId: String, link: CarLink) = autoTrips.setCarLink(vehicleId, link)

        override suspend fun unlinkCar(vehicleId: String) = autoTrips.setCarLink(vehicleId, null)
    }

    @Test
    fun carBluetoothIsOfferedWhenEditingACarAndLinks() = runTest {
        assertThat(viewModel().state.value.showCarBluetooth).isFalse()
        val vm = viewModel("safari")
        assertThat(vm.state.value.showCarBluetooth).isTrue()
        vm.linkCar(CarLink("AA:BB:CC:DD:EE:FF", 3, "Safari Audio"))
        assertThat(vm.state.value.carLinkName).isEqualTo("Safari Audio")
        assertThat(autoTrips.settings.value.carLinks.keys).containsExactly("safari")
        vm.unlinkCar()
        assertThat(vm.state.value.carLinked).isFalse()
        // A two-wheeler has no car kit to link.
        vm.onTypeChange(VehicleType.Scooter)
        assertThat(vm.state.value.showCarBluetooth).isFalse()
    }

    @Test
    fun saveWithEmptyFormShowsRequiredErrorsAndDoesNotSave() = runTest {
        val vm = viewModel()
        vm.save()
        assertThat(
            vm.state.value.errors,
        ).containsExactly(VehicleDraftError.NameRequired, VehicleDraftError.OdometerRequired)
        assertThat(vehicles.addedDrafts).isEmpty()
    }

    @Test
    fun errorsUpdateAsTheUserFixesThemAfterTheFirstAttempt() = runTest {
        val vm = viewModel()
        vm.save()
        vm.onNameChange("Activa")
        assertThat(vm.state.value.errors).containsExactly(VehicleDraftError.OdometerRequired)
    }

    @Test
    fun savingAddsTheVehicleSelectsItAndEmitsSaved() = runTest {
        val vm = viewModel(onboarding = true)
        vm.onTypeChange(VehicleType.Car)
        vm.onFuelSetupChange(FuelSetup.CngPetrol)
        vm.onNameChange("Ertiga")
        vm.onOdometerChange("31,045")
        vm.onCapacityChange(0, "12")
        vm.onCapacityChange(1, "45")
        vm.onEmissionNormChange(EmissionNorm.BS6)
        vm.save()

        val event = vm.events.first() as VehicleEditorEvent.Saved
        assertThat(event.onboarding).isTrue()
        val draft = vehicles.addedDrafts.single()
        assertThat(draft.odometerKm).isEqualTo(31_045.0)
        assertThat(draft.tankCapacities).containsExactly(12.0, 45.0).inOrder()
        assertThat(draft.emissionNorm).isEqualTo(EmissionNorm.BS6)
        assertThat(userData.userData.value.selectedVehicleId).isEqualTo(event.vehicleId)
    }

    @Test
    fun switchingFuelSetupResizesCapacityFields() = runTest {
        val vm = viewModel()
        vm.onFuelSetupChange(FuelSetup.CngPetrol)
        assertThat(vm.state.value.capacities).hasSize(2)
        vm.onFuelSetupChange(FuelSetup.Electric)
        assertThat(vm.state.value.capacities).hasSize(1)
    }

    @Test
    fun editModeLoadsTheVehicleLocksFuelAndUpdates() = runTest {
        val vm = viewModel(vehicleId = SampleVehicles.safari.id)
        assertThat(vm.state.value.name).isEqualTo("Safari")
        assertThat(vm.state.value.odometer).isEqualTo("42,318")

        vm.onFuelSetupChange(FuelSetup.Petrol) // ignored while editing
        vm.onNameChange("Safari Dicor")
        vm.save()
        vm.events.first()

        val updated = vehicles.observeVehicle(SampleVehicles.safari.id).first()!!
        assertThat(updated.name).isEqualTo("Safari Dicor")
        assertThat(updated.tanks).isEqualTo(SampleVehicles.safari.tanks)
        assertThat(vehicles.addedDrafts).isEmpty()
    }

    @Test
    fun detailsGradeAndHybridAreSavedAndLoadedBack() = runTest {
        val vm = viewModel()
        vm.onNameChange("City")
        vm.onOdometerChange("8200")
        vm.onDetailChange(VehicleDetail.Make, "Honda")
        vm.onDetailChange(VehicleDetail.Year, "20a25")
        vm.onDetailChange(VehicleDetail.PurchasePrice, "20,50,000")
        vm.onPurchaseDateChange(LocalDate.of(2025, 2, 14))
        vm.onHybridChange(true)
        vm.onDefaultGradeChange(FuelGrade.E20)
        vm.save()
        val id = (vm.events.first() as VehicleEditorEvent.Saved).vehicleId
        val draft = vehicles.addedDrafts.single()
        assertThat(draft.year).isEqualTo(2025)
        assertThat(draft.purchasePrice).isEqualTo(Money(205_000_000))
        assertThat(draft.isHybrid).isTrue()
        assertThat(draft.defaultGrade).isEqualTo(FuelGrade.E20)

        val edit = viewModel(vehicleId = id)
        assertThat(edit.state.value.detail(VehicleDetail.Make)).isEqualTo("Honda")
        assertThat(edit.state.value.purchaseDate).isEqualTo(LocalDate.of(2025, 2, 14))
        assertThat(edit.state.value.defaultGrade).isEqualTo(FuelGrade.E20)
        // A purchase reading above today's is flagged.
        edit.onDetailChange(VehicleDetail.PurchaseOdometer, "9000")
        edit.save()
        assertThat(edit.state.value.errors).containsExactly(VehicleDraftError.PurchaseOdometerInvalid)
    }
}

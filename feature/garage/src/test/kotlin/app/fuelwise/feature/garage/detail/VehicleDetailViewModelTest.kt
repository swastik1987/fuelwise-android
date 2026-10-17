package app.fuelwise.feature.garage.detail

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VehicleDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari))
    private val viewModel = VehicleDetailViewModel(SavedStateHandle(mapOf("vehicleId" to "safari")), vehicles)

    @Test
    fun deleteConfirmationNeedsTheVehicleName() {
        val safari = SampleVehicles.safari
        assertThat(viewModel.confirmationMatches("", safari)).isFalse()
        assertThat(viewModel.confirmationMatches("Safar", safari)).isFalse()
        assertThat(viewModel.confirmationMatches(" safari ", safari)).isTrue()
    }

    @Test
    fun archiveTogglesAndHidesFromTheSwitcher() = runTest {
        viewModel.setArchived(true)
        assertThat(vehicles.observeVehicle("safari").first()!!.isArchived).isTrue()
        assertThat(vehicles.observeVehicles().first()).isEmpty()
        viewModel.setArchived(false)
        assertThat(vehicles.observeVehicles().first()).hasSize(1)
    }
}

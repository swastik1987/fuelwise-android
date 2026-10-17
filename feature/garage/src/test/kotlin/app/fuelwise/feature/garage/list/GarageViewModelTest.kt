package app.fuelwise.feature.garage.list

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.feature.garage.PENDING_DELETE_VEHICLE_ID
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GarageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val vehicles = FakeVehicleRepository(SampleVehicles.all)
    private val handle = SavedStateHandle()
    private val viewModel = GarageViewModel(vehicles, handle)

    @Test
    fun splitsActiveAndArchivedVehicles() = runTest {
        viewModel.uiState.test {
            val ready = awaitItem().let { if (it is GarageUiState.Loading) awaitItem() else it } as GarageUiState.Ready
            assertThat(ready.active.map { it.name }).containsExactly("Safari", "Ertiga", "Nexon EV").inOrder()
            assertThat(ready.archived.map { it.name }).containsExactly("Activa")
        }
    }

    @Test
    fun pendingDeleteFromDetailDeletesAndOffersUndo() = runTest {
        viewModel.events.test {
            handle[PENDING_DELETE_VEHICLE_ID] = SampleVehicles.ertiga.id
            val event = awaitItem() as GarageEvent.VehicleDeleted
            assertThat(event.name).isEqualTo("Ertiga")
            assertThat(vehicles.observeVehicle(SampleVehicles.ertiga.id).first()).isNull()

            viewModel.undoDelete(event.token)
            assertThat(vehicles.observeVehicle(SampleVehicles.ertiga.id).first()).isNotNull()
        }
    }
}

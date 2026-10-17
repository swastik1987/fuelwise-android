package app.fuelwise.feature.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.domain.summary.ComputeVehicleSummary
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeLogbookRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 16, 10)), zone)
    private val userData = FakeUserDataRepository()

    private fun viewModel(vehicles: List<Vehicle>, entries: FakeEntryRepository): HomeViewModel {
        val consumption = ComputeConsumption()
        return HomeViewModel(
            vehicles = FakeVehicleRepository(vehicles),
            userData = userData,
            entries = entries,
            logbook = FakeLogbookRepository(entries, zone),
            categories = FakeCategoryRepository(),
            summarize = ComputeVehicleSummary(consumption, ComputeCostMetrics(), ComputePredictions(clock), clock),
            lookupIntervals = LookupIntervals(consumption),
            zoneId = zone,
            clock = clock,
        )
    }

    @Test
    fun noVehicles() = runTest {
        val state = viewModel(emptyList(), FakeEntryRepository()).uiState.first { it != HomeUiState.Loading }
        assertThat(state).isEqualTo(HomeUiState.NoVehicles)
    }

    @Test
    fun brandNewVehicleHasNoMileage() = runTest {
        val state = viewModel(listOf(SampleVehicles.safari), FakeEntryRepository()).uiState.first {
            it is HomeUiState.Ready
        }
        state as HomeUiState.Ready
        assertThat(state.summary.hasMileage).isFalse()
        assertThat(state.recent).isEmpty()
    }

    @Test
    fun dashboardWithHistory() = runTest {
        val entries = FakeEntryRepository(SampleEntries.safariRefuels, listOf(SampleEntries.safariService))
        val state = viewModel(listOf(SampleVehicles.safari), entries).uiState.first {
            it is HomeUiState.Ready
        } as HomeUiState.Ready
        assertThat(state.summary.hasMileage).isTrue()
        assertThat(state.summary.lastRefuel?.id).isEqualTo("r4")
        assertThat(state.recent.map { it.id }).containsExactly("r4", "e1", "r3").inOrder()
        assertThat(state.kmPerUnit.keys).containsExactly("r2", "r3", "r4")
        assertThat(state.previousMonthLabel).isEqualTo("Sep")
    }

    @Test
    fun hideAmountsIsRemembered() = runTest {
        val vm = viewModel(listOf(SampleVehicles.safari), FakeEntryRepository())
        vm.setHideAmounts(true)
        val state = vm.uiState.first { it is HomeUiState.Ready && it.hideAmounts }
        assertThat((state as HomeUiState.Ready).hideAmounts).isTrue()
    }
}

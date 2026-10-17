package app.fuelwise.feature.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
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
class EntryDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val entries = FakeEntryRepository(SampleEntries.safariRefuels, listOf(SampleEntries.safariService))

    private fun viewModel(source: EntrySource, id: String) = EntryDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("source" to source.name, "id" to id)),
        entries = entries,
        vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari)),
        categories = FakeCategoryRepository(),
        lookupIntervals = LookupIntervals(ComputeConsumption()),
    )

    @Test
    fun fillUpShowsItsInterval() = runTest {
        val state = viewModel(EntrySource.Refuel, "r3").uiState.first {
            it is EntryDetailUiState.FillUp
        } as EntryDetailUiState.FillUp
        assertThat(state.distanceSincePrevious).isEqualTo(640.0)
        val interval = (state.result as IntervalResult.Measured).interval
        assertThat(interval.distanceKm).isEqualTo(640.0)
        assertThat(interval.quantity).isEqualTo(41.0)
    }

    @Test
    fun firstFillIsTheBaseline() = runTest {
        val state = viewModel(EntrySource.Refuel, "r1").uiState.first {
            it is EntryDetailUiState.FillUp
        } as EntryDetailUiState.FillUp
        assertThat(state.result).isEqualTo(IntervalResult.Baseline)
        assertThat(state.distanceSincePrevious).isNull()
    }

    @Test
    fun expenseShowsItsCategory() = runTest {
        val state = viewModel(EntrySource.Expense, "e1").uiState.first {
            it is EntryDetailUiState.Cost
        } as EntryDetailUiState.Cost
        assertThat(state.category?.systemKey).isEqualTo(SystemCategory.Service)
    }

    @Test
    fun deleteTombstonesAndCloses() = runTest {
        val vm = viewModel(EntrySource.Expense, "e1")
        vm.uiState.first { it is EntryDetailUiState.Cost }
        vm.delete()
        vm.deleted.first()
        assertThat(entries.expenses).isEmpty()
    }
}

package app.fuelwise.feature.logbook

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.LogbookKind
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
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LogbookViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val entries = FakeEntryRepository(SampleEntries.safariRefuels, listOf(SampleEntries.safariService))

    private fun viewModel(entries: FakeEntryRepository = this.entries) = LogbookViewModel(
        logbook = FakeLogbookRepository(entries, zone),
        entries = entries,
        vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari)),
        categories = FakeCategoryRepository(),
        userData = FakeUserDataRepository(),
        lookupIntervals = LookupIntervals(ComputeConsumption()),
        zoneId = zone,
        clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 16, 10)), zone),
    )

    @Test
    fun groupsByMonthNewestFirst() = runTest {
        val newestFirst = (
            SampleEntries.safariRefuels.map(LogbookEntry::of) +
                LogbookEntry.of(SampleEntries.safariService)
            )
            .sortedByDescending { it.dateTimeUtcMs }
        val items = flowOf(PagingData.from(newestFirst)).map { it.withMonthHeaders(zone) }.asSnapshot()
        assertThat(items.filterIsInstance<LogbookItem.Header>().map { it.month })
            .containsExactly(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2026, 8)).inOrder()
        assertThat(items.first()).isEqualTo(LogbookItem.Header(YearMonth.of(2026, 10)))
        assertThat(items.filterIsInstance<LogbookItem.Row>().map { it.entry.id })
            .containsExactly("r4", "e1", "r3", "r2", "r1").inOrder()
    }

    @Test
    fun filterByKind() = runTest {
        val vm = viewModel()
        vm.toggleKind(LogbookKind.Expense)
        assertThat(vm.chrome.first { it.kinds.isNotEmpty() }.filtering).isTrue()
        vm.clearKinds()
        assertThat(vm.chrome.first { it.kinds.isEmpty() }.filtering).isFalse()
    }

    @Test
    fun chromeHasEfficiencyForClosingFills() = runTest {
        val chrome = viewModel().chrome.first { it.selectedVehicle != null && it.monthTotals.isNotEmpty() }
        assertThat(chrome.kmPerUnit.keys).containsExactly("r2", "r3", "r4")
        assertThat(chrome.monthTotals.keys).containsExactly("2026-08", "2026-09", "2026-10")
    }

    @Test
    fun deleteThenUndo() = runTest {
        val vm = viewModel()
        vm.delete(LogbookEntry.of(SampleEntries.safariService))
        val event = vm.events.first() as LogbookEvent.Deleted
        assertThat(entries.expenses).isEmpty()
        vm.undo(event.token)
        assertThat(entries.expenses.map { it.id }).containsExactly("e1")
    }

    @Test
    fun mapShowsGeotaggedFillUpsUnderTheSameFilters() = runTest {
        // Two fill-ups with a location (Koramangala, Indiranagar); the others have none yet.
        val refuels = SampleEntries.safariRefuels.mapIndexed { i, r ->
            when (i) {
                2 -> r.copy(lat = 12.9352, lng = 77.6245)
                3 -> r.copy(lat = 12.9719, lng = 77.6412)
                else -> r
            }
        }
        val vm = viewModel(FakeEntryRepository(refuels, listOf(SampleEntries.safariService)))
        assertThat(vm.chrome.first { it.selectedVehicle != null }.pins).isEmpty()

        vm.toggleMap()
        val pins = vm.chrome.first { it.mapMode && it.pins.isNotEmpty() }.pins
        assertThat(pins.map { it.entry.id }).containsExactly("r3", "r4")
        assertThat(pins.first().energy).isEqualTo(EnergyType.DIESEL)

        vm.selectPin("r4")
        assertThat(vm.chrome.first { it.selectedPinId != null }.selectedPinId).isEqualTo("r4")
        vm.toggleKind(LogbookKind.Expense)
        assertThat(vm.chrome.first { LogbookKind.Expense in it.kinds }.pins).isEmpty()
        vm.setQuery("Indiranagar")
        vm.clearKinds()
        assertThat(vm.chrome.first { it.kinds.isEmpty() && it.query.isNotEmpty() }.pins.map { it.entry.id })
            .containsExactly("r4")
    }
}

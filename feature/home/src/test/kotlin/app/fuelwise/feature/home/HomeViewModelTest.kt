package app.fuelwise.feature.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.domain.insights.ComputeMilestones
import app.fuelwise.core.domain.insights.Milestone
import app.fuelwise.core.domain.prices.SummarizeCityPrices
import app.fuelwise.core.domain.summary.ComputeVehicleSummary
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeCityRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeFuelPriceRepository
import app.fuelwise.core.testing.FakeLogbookRepository
import app.fuelwise.core.testing.FakePriceSettingsRepository
import app.fuelwise.core.testing.FakeRecurrenceRepository
import app.fuelwise.core.testing.FakeReminderRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.core.testing.reminderStatuses
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

    private fun viewModel(
        vehicles: List<Vehicle>,
        entries: FakeEntryRepository,
        trips: FakeTripRepository = FakeTripRepository(),
    ): HomeViewModel {
        val vehicleRepo = FakeVehicleRepository(vehicles)
        val consumption = ComputeConsumption()
        return HomeViewModel(
            vehicles = vehicleRepo,
            userData = userData,
            entries = entries,
            trips = trips,
            logbook = FakeLogbookRepository(entries, zone),
            categories = FakeCategoryRepository(),
            summarize = ComputeVehicleSummary(consumption, ComputeCostMetrics(), ComputePredictions(clock), clock),
            lookupIntervals = LookupIntervals(consumption),
            milestones = ComputeMilestones(consumption, clock),
            care = HomeCareSource(
                reminderStatuses(FakeReminderRepository(), vehicleRepo, entries, clock),
                FakeRecurrenceRepository(),
            ),
            homePrices = HomePricesSource(
                FakePriceSettingsRepository(),
                FakeFuelPriceRepository(),
                FakeCityRepository(),
                SummarizeCityPrices(),
                clock,
                zone,
            ),
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
    fun milestoneShownOnce() = runTest {
        val entries = FakeEntryRepository(SampleEntries.safariRefuels, listOf(SampleEntries.safariService))
        val vm = viewModel(listOf(SampleVehicles.safari), entries)
        val first = vm.uiState.first { it is HomeUiState.Ready } as HomeUiState.Ready
        assertThat(first.milestone?.kind).isEqualTo(Milestone.Kind.FirstThousandKm)
        vm.dismissMilestone(first.milestone!!.key)
        val next = vm.uiState.first { it is HomeUiState.Ready && it.milestone?.kind != Milestone.Kind.FirstThousandKm }
        assertThat((next as HomeUiState.Ready).milestone?.kind).isNotEqualTo(Milestone.Kind.FirstThousandKm)
    }

    @Test
    fun tripsTileCountsThisMonthsFinishedTrips() = runTest {
        fun trip(id: String, month: Int, km: Double) =
            Trip(id, "safari", SampleEntries.at(2026, month, 10), zone.id, km, TripPurpose.Personal)
        val trips = FakeTripRepository(listOf(trip("t1", 10, 23.4), trip("t2", 10, 146.0), trip("t3", 9, 40.0)))
        val state = viewModel(listOf(SampleVehicles.safari), FakeEntryRepository(), trips).uiState.first {
            it is HomeUiState.Ready
        } as HomeUiState.Ready
        assertThat(state.trips.count).isEqualTo(2)
        assertThat(state.trips.distanceKm).isWithin(1e-9).of(169.4)
    }

    @Test
    fun hideAmountsIsRemembered() = runTest {
        val vm = viewModel(listOf(SampleVehicles.safari), FakeEntryRepository())
        vm.setHideAmounts(true)
        val state = vm.uiState.first { it is HomeUiState.Ready && it.hideAmounts }
        assertThat((state as HomeUiState.Ready).hideAmounts).isTrue()
    }
}

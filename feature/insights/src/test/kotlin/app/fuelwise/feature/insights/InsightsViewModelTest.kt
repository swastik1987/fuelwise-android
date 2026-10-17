package app.fuelwise.feature.insights

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.insights.ComputeInsights
import app.fuelwise.core.domain.insights.InsightPeriod
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeEntryRepository
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
class InsightsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 16, 10)), zone)

    private fun viewModel(vehicles: List<Vehicle> = listOf(SampleVehicles.safari, SampleVehicles.ertiga)) =
        InsightsViewModel(
            InsightsSource(
                vehicles = FakeVehicleRepository(vehicles),
                entries = FakeEntryRepository(SampleEntries.safariRefuels, listOf(SampleEntries.safariService)),
                categories = FakeCategoryRepository(),
                userData = FakeUserDataRepository(),
                compute = ComputeInsights(ComputeConsumption(), ComputeCostMetrics(), clock),
                zoneId = zone,
            ),
        )

    @Test
    fun monthByDefaultForTheFirstVehicle() = runTest {
        val state = viewModel().state.first { it != null }!!
        assertThat(state.period).isEqualTo(InsightPeriod.Month)
        assertThat(state.selected?.id).isEqualTo("safari")
        assertThat(state.report!!.fillCount).isEqualTo(1)
    }

    @Test
    fun allTimeShowsEveryInterval() = runTest {
        val vm = viewModel()
        vm.selectPeriod(InsightPeriod.All)
        val report = vm.state.first { it?.period == InsightPeriod.All }!!.report!!
        assertThat(report.mileage).hasSize(3)
        assertThat(report.fillCount).isEqualTo(4)
    }

    @Test
    fun allVehiclesDropsPerVehicleCharts() = runTest {
        val vm = viewModel()
        vm.selectVehicle(null)
        val state = vm.state.first { it?.allVehicles == true }!!
        assertThat(state.report!!.energyType).isNull()
        assertThat(state.report.mileage).isEmpty()
    }

    @Test
    fun noVehiclesNoReport() = runTest {
        val state = viewModel(emptyList()).state.first { it != null }!!
        assertThat(state.report).isNull()
    }
}

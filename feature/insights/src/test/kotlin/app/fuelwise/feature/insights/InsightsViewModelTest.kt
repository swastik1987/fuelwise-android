package app.fuelwise.feature.insights

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.insights.ComputeInsights
import app.fuelwise.core.domain.insights.CustomRange
import app.fuelwise.core.domain.insights.InsightPeriod
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeFuelPriceRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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

    private fun viewModel(
        vehicles: List<Vehicle> = listOf(SampleVehicles.safari, SampleVehicles.ertiga),
        userData: FakeUserDataRepository = FakeUserDataRepository(),
        prices: FakeFuelPriceRepository = FakeFuelPriceRepository(),
    ) = InsightsViewModel(
        InsightsSource(
            vehicles = FakeVehicleRepository(vehicles),
            entries = FakeEntryRepository(SampleEntries.safariRefuels, listOf(SampleEntries.safariService)),
            categories = FakeCategoryRepository(),
            trips = FakeTripRepository(),
            userData = userData,
            cityPrices = prices,
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
    fun pricePaidGetsTheHomeCityOverlay() = runTest {
        val userData = FakeUserDataRepository().apply { setHomeCity("bengaluru") }
        val prices = FakeFuelPriceRepository().apply {
            saveManual("bengaluru", PriceFuel.Diesel, LocalDate.of(2026, 10, 14), Money(8_895))
            // Another fuel and another city never show on a diesel Safari.
            saveManual("bengaluru", PriceFuel.Petrol, LocalDate.of(2026, 10, 14), Money(10_292))
            saveManual("delhi", PriceFuel.Diesel, LocalDate.of(2026, 10, 14), Money(8_762))
        }
        val vm = viewModel(userData = userData, prices = prices)
        vm.selectPeriod(InsightPeriod.All)
        val state = vm.state.first { it?.period == InsightPeriod.All && it.cityPrices.any { p -> p != null } }!!
        assertThat(state.cityPrices).hasSize(state.report!!.prices.size)
        assertThat(state.cityPrices.filterNotNull()).containsExactly(Money(8_895))
    }

    @Test
    fun aPickedRangeCoversOnlyThoseDays() = runTest {
        val vm = viewModel()
        // 1 Aug – 30 Sep, picked backwards: the range is put in order.
        vm.selectCustom(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 8, 1))
        val state = vm.state.first { it?.period == InsightPeriod.Custom }!!
        assertThat(state.custom).isEqualTo(CustomRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30)))
        val all = viewModel().apply {
            selectPeriod(InsightPeriod.All)
        }.state.first { it?.period == InsightPeriod.All }!!
        assertThat(state.report!!.fillCount).isLessThan(all.report!!.fillCount)
        assertThat(state.report.fillCount).isGreaterThan(0)
    }

    @Test
    fun costOfOwnershipSpreadsThePriceOverTheChosenYears() = runTest {
        val bought = SampleVehicles.safari.copy(
            purchaseDate = LocalDate.of(2024, 3, 9),
            purchasePrice = Money(215_000_000),
        )
        val vm = viewModel(vehicles = listOf(bought))
        vm.selectPeriod(InsightPeriod.TwelveMonths)
        val five = vm.state.first { it?.report?.ownership != null }!!.report!!.ownership!!
        assertThat(five.years).isEqualTo(5)
        vm.setOwnershipYears(10)
        val ten = vm.state.first { it?.report?.ownership?.years == 10 }!!.report!!.ownership!!
        assertThat(ten.purchaseShare.paise).isLessThan(five.purchaseShare.paise)
        assertThat(ten.running).isEqualTo(five.running)
        // Without a price there is nothing to spread.
        assertThat(viewModel().state.first { it?.report != null }!!.report!!.ownership).isNull()
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

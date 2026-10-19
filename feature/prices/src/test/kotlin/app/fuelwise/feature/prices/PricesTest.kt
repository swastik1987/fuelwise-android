package app.fuelwise.feature.prices

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.repository.PriceSettings
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.prices.SummarizeCityPrices
import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.FuelPriceOrigin
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import app.fuelwise.core.testing.FakeCityRepository
import app.fuelwise.core.testing.FakeFuelPriceRepository
import app.fuelwise.core.testing.FakePriceSettingsRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class PricesTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 19)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    // Illustrative prices (design.md §11), not live data.
    private val samplePrices = listOf(
        price("bengaluru", PriceFuel.Petrol, today.minusDays(20), 10_250),
        price("bengaluru", PriceFuel.Petrol, today.minusDays(3), 10_292),
        price("bengaluru", PriceFuel.Diesel, today.minusDays(3), 8_895),
        price("bengaluru", PriceFuel.Cng, today.minusDays(12), 9_000),
        price("bengaluru", PriceFuel.Cng, today, 8_900),
        price("delhi", PriceFuel.Petrol, today, 9_472),
        price("delhi", PriceFuel.Diesel, today, 8_762),
        price("mumbai", PriceFuel.Petrol, today, 10_331),
    )

    private val settings = FakePriceSettingsRepository()
    private val prices = FakeFuelPriceRepository(samplePrices)

    private fun viewModel(vehicles: FakeVehicleRepository = FakeVehicleRepository(listOf(SampleVehicles.safari))) =
        PricesViewModel(
            settings = settings,
            prices = prices,
            cities = FakeCityRepository(),
            vehicles = vehicles,
            userData = FakeUserDataRepository(),
            summarize = SummarizeCityPrices(),
            clock = clock,
            zoneId = zone,
        )

    @Test
    fun choosingAHomeCityShowsItsPrices() = runTest {
        val vm = viewModel()
        assertThat(vm.state.first { !it.loading }.home).isNull()
        vm.setHome("bengaluru")
        val home = vm.state.first { it.home != null }.home!!
        val petrol = home.summary.row(PriceFuel.Petrol)!!
        assertThat(petrol.latest?.price).isEqualTo(Money(10_292))
        assertThat(petrol.change).isEqualTo(Money(42))
        assertThat(home.summary.row(PriceFuel.PremiumPetrol)!!.latest).isNull()
    }

    @Test
    fun pinUpToThreeCitiesNeverTheHomeCity() = runTest {
        val vm = viewModel()
        vm.setHome("bengaluru")
        listOf("delhi", "mumbai", "bengaluru", "chennai", "pune").forEach(vm::pin)
        val s = vm.state.first { it.pinned.size == PriceSettings.MAX_PINNED }
        assertThat(s.pinned.map { it.city.id }).containsExactly("delhi", "mumbai", "chennai").inOrder()
        assertThat(s.canPin).isFalse()
        vm.unpin("mumbai")
        assertThat(vm.state.first { it.pinned.size == 2 }.canPin).isTrue()
        // A pinned city chosen as home leaves the compare list.
        vm.setHome("delhi")
        assertThat(vm.state.first { it.home?.city?.id == "delhi" }.pinned.map { it.city.id }).containsExactly("chennai")
    }

    @Test
    fun aTypedPriceIsSavedForTheDayAndAFutureDateIsClamped() = runTest {
        val vm = viewModel()
        vm.setHome("pune")
        assertThat(vm.savePrice("pune", PriceFuel.Petrol, today.plusDays(2), "104.31")).isTrue()
        assertThat(vm.savePrice("pune", PriceFuel.Diesel, today, "")).isFalse()
        val row = vm.state.first { it.home?.summary?.hasAny == true }.home!!.summary.row(PriceFuel.Petrol)!!
        assertThat(row.latest?.price).isEqualTo(Money(10_431))
        assertThat(row.latest?.date).isEqualTo(today)
        assertThat(row.latest?.origin).isEqualTo(FuelPriceOrigin.Manual)
    }

    @Test
    fun findAPumpSearchesForTheVehiclesFuel() = runTest {
        val vm = viewModel(FakeVehicleRepository(listOf(SampleVehicles.ertiga)))
        vm.state.first { !it.loading }
        vm.findPump()
        val event = vm.eventFlow.first() as PricesEvent.OpenMaps
        assertThat(event.url).isEqualTo("https://www.google.com/maps/search/?api=1&query=CNG+station+near+me")
    }

    @Test
    fun emptyCityRowInvitesTodaysPrice() {
        compose.setContent { FuelwiseTheme { PricesContent(ready(home = "pune"), PricesActions()) } }
        compose.onNodeWithText("Petrol").assertIsDisplayed()
        compose.onNodeWithText("No price yet — add today's price").assertIsDisplayed()
        compose.onNodeWithText("Petrol").performClick()
        compose.onNodeWithText("Save price").assertIsDisplayed()
    }

    @Test fun pricesLight() = capture("prices_light", false) { PricesContent(ready(), PricesActions()) }

    @Test fun pricesDark() = capture("prices_dark", true) { PricesContent(ready(), PricesActions()) }

    @Test fun noCityLight() = capture("prices_no_city_light", false) {
        PricesContent(PricesUiState(loading = false, catalog = FakeCityRepository.SAMPLE), PricesActions())
    }

    @Test fun noCityDark() = capture("prices_no_city_dark", true) {
        PricesContent(PricesUiState(loading = false, catalog = FakeCityRepository.SAMPLE), PricesActions())
    }

    private fun ready(home: String = "bengaluru"): PricesUiState {
        val summarize = SummarizeCityPrices()
        fun of(id: String) = CityPrices(FakeCityRepository.city(id), summarize(id, samplePrices, today))
        return PricesUiState(
            loading = false,
            catalog = FakeCityRepository.SAMPLE,
            home = of(home),
            pinned = listOf(of("delhi"), of("mumbai")),
            energy = SampleVehicles.safari.primaryTank?.energyType,
            today = today,
        )
    }

    private fun price(city: String, fuel: PriceFuel, date: LocalDate, paise: Long) =
        FuelPrice("$city-$fuel-$date", city, fuel, date, Money(paise))

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

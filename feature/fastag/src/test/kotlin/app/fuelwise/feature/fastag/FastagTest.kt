package app.fuelwise.feature.fastag

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.recurrence.FastagBalance
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeFastagRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
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
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class FastagTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 10)), zone)
    private val tolls = listOf(
        Expense(
            "t1",
            "safari",
            SystemCategory.TollFastag.id,
            SampleEntries.at(2026, 10, 12),
            zone.id,
            Money(23_500),
            tollPlaza = "Electronic City",
        ),
        Expense(
            "t2",
            "safari",
            SystemCategory.TollFastag.id,
            SampleEntries.at(2026, 10, 14),
            zone.id,
            Money(16_500),
            tollPlaza = "Attibele",
        ),
    )
    private val fastag = FakeFastagRepository()

    private fun viewModel() = FastagViewModel(
        fastag,
        FakeVehicleRepository(listOf(SampleVehicles.safari)),
        FakeEntryRepository(expenses = tolls),
        clock,
        zone,
    )

    @Test
    fun balanceRunsFromTheAnchor() = runTest {
        val vm = viewModel()
        vm.saveAccount(AccountInput("safari", null, "ICICI Bank", "34161234", "600", "300"))
        val card = vm.state.first { it.cards.firstOrNull()?.account != null }.cards.single()
        assertThat(card.account?.tagLast4).isEqualTo("1234")
        // Anchored on 10 Oct at ₹600; both tolls (₹235 + ₹165) come after it.
        assertThat(card.balance?.estimate).isEqualTo(Money(20_000))
        assertThat(card.balance?.isLow).isTrue()
        vm.addRecharge(card.account!!, "500", "")
        val after = vm.state.first { s -> s.cards.single().recent.any { it.isRecharge } }.cards.single()
        assertThat(after.balance?.estimate).isEqualTo(Money(70_000))
        assertThat(after.recent.map { it.label }).contains("Attibele")
    }

    @Test fun cardsLight() = capture("fastag_light", false) {
        FastagContent(
            FastagUiState(
                loading = false,
                cards = listOf(
                    FastagCard(
                        SampleVehicles.safari,
                        FastagAccount("a", "safari", "ICICI Bank", "1234", Money(60_000), clock.millis()),
                        FastagBalance(Money(20_000), isLow = true),
                        listOf(
                            FastagItem("t2", SampleEntries.at(2026, 10, 14), Money(16_500), "Attibele", false),
                            FastagItem("r1", SampleEntries.at(2026, 10, 11), Money(50_000), null, true),
                        ),
                    ),
                    FastagCard(SampleVehicles.ertiga, null, null, emptyList()),
                ),
            ),
            FastagActions(),
        )
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

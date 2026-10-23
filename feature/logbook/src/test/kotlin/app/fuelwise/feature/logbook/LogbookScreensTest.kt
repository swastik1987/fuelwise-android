package app.fuelwise.feature.logbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.core.ui.map.LocalMapsEnabled
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.YearMonth
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of design.md screens 12 and 13. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class LogbookScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val categories: Map<String, Category> = FakeCategoryRepository.SYSTEM.associateBy { it.id }

    private val chrome = LogbookChrome(
        vehicles = listOf(SampleVehicles.safari),
        selectedVehicle = SampleVehicles.safari,
        categories = categories,
        kmPerUnit = mapOf("r2" to 15.29, "r3" to 15.61, "r4" to 15.42),
        monthTotals = mapOf("2026-10" to Money(843_986), "2026-09" to Money(744_190)),
        currentYear = 2026,
    )

    private val items = listOf(
        LogbookItem.Header(YearMonth.of(2026, 10)),
        LogbookItem.Row(LogbookEntry.of(SampleEntries.safariRefuels[3])),
        LogbookItem.Row(LogbookEntry.of(SampleEntries.safariService)),
        LogbookItem.Header(YearMonth.of(2026, 9)),
        LogbookItem.Row(LogbookEntry.of(SampleEntries.safariRefuels[2])),
        LogbookItem.Row(LogbookEntry.of(SampleEntries.safariRefuels[1])),
    )

    private val fillUp = EntryDetailUiState.FillUp(
        refuel = SampleEntries.safariRefuels[2],
        vehicle = SampleVehicles.safari,
        result = IntervalResult.Measured(Interval(640.0, 41.0)),
        distanceSincePrevious = 640.0,
    )

    @Test fun logbookLight() = capture("logbook_light", false) { Logbook() }

    @Test fun logbookDark() = capture("logbook_dark", true) { Logbook() }

    @Test fun logbookFilteredDark() = capture("logbook_filtered_dark", true) {
        Logbook(
            chrome.copy(
                sheet = LogbookSheetFilters(
                    range = app.fuelwise.core.domain.entry.EntryRangePreset.ThisMonth,
                    payments = setOf(app.fuelwise.core.model.PaymentMethod.Upi),
                ),
            ),
        )
    }

    @Test fun logbookMapLight() = capture("logbook_map_light", false) { Logbook(mapChrome()) }

    @Test fun logbookMapDark() = capture("logbook_map_dark", true) { Logbook(mapChrome()) }

    @Test fun logbookMapEmpty() = capture("logbook_map_empty_light", false) { Logbook(chrome.copy(mapMode = true)) }

    @Test fun fillUpDetailLight() = capture("entry_detail_light", false) { EntryDetailContent(fillUp, {}, {}, {}) }

    @Test fun fillUpDetailDark() = capture("entry_detail_dark", true) { EntryDetailContent(fillUp, {}, {}, {}) }

    @Test fun expenseDetailLight() = capture("expense_detail_light", false) {
        EntryDetailContent(
            EntryDetailUiState.Cost(
                SampleEntries.safariService,
                SampleVehicles.safari,
                categories[SystemCategory.Service.id],
            ),
            {},
            {},
            {},
        )
    }

    @Composable
    private fun Logbook(chrome: LogbookChrome = this.chrome) {
        val paged = flowOf(PagingData.from(items)).collectAsLazyPagingItems()
        LogbookContent(chrome, paged, LogbookActions())
    }

    private fun mapChrome(): LogbookChrome {
        val koramangala = SampleEntries.safariRefuels[2]
        val indiranagar = SampleEntries.safariRefuels[3]
        return chrome.copy(
            mapMode = true,
            pins = listOf(
                LogbookPin(LogbookEntry.of(koramangala), 12.9352, 77.6245, EnergyType.DIESEL),
                LogbookPin(LogbookEntry.of(indiranagar), 12.9719, 77.6412, EnergyType.DIESEL),
            ),
            selectedPinId = indiranagar.id,
        )
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        // Robolectric can't run the Maps SDK: maps draw their plain fallback.
        compose.setContent {
            CompositionLocalProvider(LocalMapsEnabled provides false) { FuelwiseTheme(darkTheme = dark) { content() } }
        }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

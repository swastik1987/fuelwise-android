package app.fuelwise.feature.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.createBitmap
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.component.FwPhoto
import app.fuelwise.core.designsystem.component.FwSheetContent
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.entry.EntryFacts
import app.fuelwise.core.domain.entry.LogbookFacts
import app.fuelwise.core.domain.entry.MonthSummary
import app.fuelwise.core.domain.station.StationOption
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.LogbookEntry
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.core.ui.entry.LogbookEntryCard
import app.fuelwise.core.ui.map.LocalMapsEnabled
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import java.time.YearMonth
import java.time.ZoneId
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
        facts = LogbookFacts(ComputeConsumption())(
            SampleEntries.safariRefuels,
            listOf(SampleEntries.safariService),
            listOf(SampleVehicles.safari),
            ZoneId.of("Asia/Kolkata"),
        ),
        monthTotals = mapOf("2026-10" to Money(843_986), "2026-09" to Money(744_190)),
        monthSummaries = mapOf(
            "2026-10" to MonthSummary.Counts(fills = 1, charges = 0, expenses = 1, tolls = 0),
            "2026-09" to MonthSummary.Counts(fills = 2, charges = 0, expenses = 0, tolls = 0),
        ),
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

    // Station and where it was (PRD F-02.1): name with the edit button, and the still map (plain fallback here).
    private val fillUpAtStation = fillUp.copy(
        refuel = fillUp.refuel.copy(stationName = "HP · Sri Lakshmi Fuels", lat = 12.97872, lng = 77.64081),
    )

    @Test fun fillUpStationLight() = capture("entry_detail_station_light", false) {
        EntryDetailContent(fillUpAtStation, {}, {}, {})
    }

    @Test fun fillUpStationDark() = capture("entry_detail_station_dark", true) {
        EntryDetailContent(fillUpAtStation, {}, {}, {})
    }

    private val stationEdit = StationEdit(
        loading = false,
        hasLocation = true,
        current = "HP · Sri Lakshmi Fuels",
        options = listOf(
            StationOption("HP · Sri Lakshmi Fuels", 12.97872, 77.64081, 33.0, usedBefore = false),
            StationOption("IOCL 100 Ft Rd", 12.97782, 77.64081, 67.0, usedBefore = true),
            StationOption("BPCL · Speed Point", 12.97952, 77.64081, 122.0, usedBefore = false),
        ),
    )

    @Test fun changeStationLight() = capture("change_station_light", false) {
        Surface(color = FwTheme.colors.surface) {
            FwSheetContent("Change station") { StationEditBody(stationEdit, StationActions()) }
        }
    }

    @Test fun changeStationTypingDark() = capture("change_station_typing_dark", true) {
        Surface(color = FwTheme.colors.surface) {
            FwSheetContent("Change station") { StationEditBody(stationEdit, StationActions(), initialQuery = "Raju") }
        }
    }

    @Test fun changeStationNoLocationLight() = capture("change_station_no_location_light", false) {
        Surface(color = FwTheme.colors.surface) {
            FwSheetContent("Change station") {
                StationEditBody(StationEdit(loading = false, hasLocation = false, current = null), StationActions())
            }
        }
    }

    @Test
    fun pickingAStationOrTypingANameCallsBack() {
        var picked: StationOption? = null
        var typed: String? = null
        val actions = StationActions(onPick = { picked = it }, onType = { typed = it })
        compose.setContent {
            CompositionLocalProvider(LocalMapsEnabled provides false) {
                FuelwiseTheme { Column { StationEditBody(stationEdit, actions) } }
            }
        }
        compose.onNodeWithText("IOCL 100 Ft Rd").performClick()
        assertThat(picked?.label).isEqualTo("IOCL 100 Ft Rd")
        compose.onNodeWithText("Search or type a name").performTextInput("Raju Fuels")
        compose.onNodeWithText("Use “Raju Fuels”").performClick()
        assertThat(typed).isEqualTo("Raju Fuels")
    }

    @Test fun expenseDetailLight() = capture("expense_detail_light", false) {
        EntryDetailContent(
            EntryDetailUiState.Cost(
                SampleEntries.safariService,
                SampleVehicles.safari,
                categories[SystemCategory.Service.id],
                photos = listOf(receipt("bill-1", 0xFFE9E4D8.toInt()), receipt("bill-2", 0xFFD8DEE9.toInt())),
            ),
            {},
            {},
            {},
        )
    }

    /** A stand-in receipt photo, drawn so the screenshot doesn't depend on a file. */
    private fun receipt(key: String, paper: Int): FwPhoto {
        val bitmap = createBitmap(300, 400)
        android.graphics.Canvas(bitmap).apply {
            drawColor(paper)
            val ink = android.graphics.Paint().apply { color = android.graphics.Color.rgb(90, 90, 90) }
            repeat(8) { i -> drawRect(30f, 40f + i * 40f, 270f - (i % 3) * 50f, 52f + i * 40f, ink) }
        }
        return FwPhoto(key, bitmap.asImageBitmap())
    }

    // The design's own examples: a fill well below average, one above, and expenses in each category family.
    private val worseFill = LogbookEntry.of(SampleEntries.safariRefuels[3]).copy(id = "w", place = "Laghumamma")
    private val betterFill = LogbookEntry.of(SampleEntries.safariRefuels[2]).copy(id = "b")
    private val service = LogbookEntry.of(SampleEntries.safariService).copy(id = "s", odometerKm = 30_662.0)
    private val battery = service.copy(id = "bt", categoryId = SystemCategory.Battery.id, odometerKm = null)
    private val purchase = service.copy(id = "p", categoryId = SystemCategory.Purchase.id)
    private val cardFacts = mapOf(
        "w" to EntryFacts.Fill(QuantityUnit.LITRE, Interval(258.0, 33.1), -0.34, null, 258.0, Money(1_260)),
        "b" to EntryFacts.Fill(QuantityUnit.LITRE, Interval(640.0, 36.0), 0.15, null, 640.0, Money(571)),
        "s" to EntryFacts.Spend(hasEarlier = true, kmSinceSame = 3_942.0, daysSinceSame = 264),
        "bt" to EntryFacts.Spend(hasEarlier = true, kmSinceSame = null, daysSinceSame = 412),
        "p" to EntryFacts.Spend(hasEarlier = false, kmSinceSame = null, daysSinceSame = null),
    )

    @Composable
    private fun Cards() {
        Column(
            Modifier.background(FwTheme.colors.bg).padding(FwTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
        ) {
            listOf(worseFill, betterFill, service, battery, purchase).forEach { entry ->
                LogbookEntryCard(entry, SampleVehicles.safari, categories, cardFacts[entry.id], onClick = {})
            }
        }
    }

    @Test fun entryCardsLight() = capture("logbook_cards_light", false) { Cards() }

    @Test fun entryCardsDark() = capture("logbook_cards_dark", true) { Cards() }

    @Test fun aCardReadsAsOneSentenceWithWordsForTheArrows() {
        compose.setContent { FuelwiseTheme { Cards() } }
        compose.onNodeWithContentDescription("34% below your average", substring = true).assertExists()
        compose.onNodeWithContentDescription("+3,942 km, 264 days ago", substring = true).assertExists()
        compose.onNodeWithContentDescription("Not logged, tap to add", substring = true).assertExists()
        compose.onNodeWithContentDescription("First log, none earlier", substring = true).assertExists()
        compose.onNodeWithText("▼ 34% vs avg").assertDoesNotExist() // Merged into the card's description.
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

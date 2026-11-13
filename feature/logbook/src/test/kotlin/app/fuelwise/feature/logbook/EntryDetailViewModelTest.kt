package app.fuelwise.feature.logbook

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.IntervalResult
import app.fuelwise.core.domain.entry.LookupIntervals
import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.station.StationFuel
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.EntrySource
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeDocumentRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeFuelPriceRepository
import app.fuelwise.core.testing.FakeStationRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EntryDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    // 100 Feet Road, Indiranagar, Bengaluru; the stations are made up. r3 was saved at the road as "Shell" (wrong).
    private val hereLat = 12.97842
    private val hereLng = 77.64081
    private val hp = Station(1, hereLat + 0.0003, hereLng, "HP", "Sri Lakshmi Fuels", setOf(StationFuel.Diesel), false)
    private val iocl = Station(2, hereLat - 0.0006, hereLng, "IndianOil", null, setOf(StationFuel.Diesel), false)

    private val entries = FakeEntryRepository(
        SampleEntries.safariRefuels.map {
            if (it.id == "r3") it.copy(stationName = "Shell", lat = hereLat, lng = hereLng) else it
        },
        listOf(SampleEntries.safariService),
    )

    private val documents = FakeDocumentRepository()

    private fun viewModel(source: EntrySource, id: String) = EntryDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("source" to source.name, "id" to id)),
        entries = entries,
        vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari)),
        categories = FakeCategoryRepository(),
        lookupIntervals = LookupIntervals(ComputeConsumption()),
        userData = FakeUserDataRepository(),
        cityPrices = FakeFuelPriceRepository(),
        documents = documents,
        stations = FakeStationRepository(listOf(hp, iocl)),
        io = main.dispatcher,
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

    @Test
    fun anEntryShowsItsPhotos() = runTest {
        val jpeg = ByteArrayOutputStream().use { out ->
            createBitmap(600, 800).compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        documents.addAttachment("e1", Attachment.OwnerType.Expense, jpeg, "image/jpeg")
        // A PDF bill is kept but isn't a thumbnail.
        documents.addAttachment("e1", Attachment.OwnerType.Expense, byteArrayOf(1), "application/pdf")
        val cost = viewModel(EntrySource.Expense, "e1").uiState.first {
            it is EntryDetailUiState.Cost && it.photos.isNotEmpty()
        } as EntryDetailUiState.Cost
        assertThat(cost.photos).hasSize(1)
    }

    /** Keeps the detail state live, as the screen does, and waits for the fill-up. */
    private suspend fun TestScope.openFillUp(id: String): EntryDetailViewModel {
        val vm = viewModel(EntrySource.Refuel, id)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it is EntryDetailUiState.FillUp }
        return vm
    }

    @Test
    fun changeStationListsStationsNearTheSavedPositionAndSavesThePick() = runTest {
        val vm = openFillUp("r3")
        vm.onEditStation()
        val edit = vm.stationEdit.first { it != null && !it.loading }!!
        assertThat(edit.hasLocation).isTrue()
        assertThat(edit.current).isEqualTo("Shell")
        assertThat(edit.options.map { it.label }).containsExactly("HP · Sri Lakshmi Fuels", "IndianOil").inOrder()

        vm.onPickStation(edit.options[1])
        assertThat(vm.stationEdit.value).isNull()
        val saved = entries.refuels.single { it.id == "r3" }
        assertThat(saved.stationName).isEqualTo("IndianOil")
        assertThat(saved.lat).isEqualTo(iocl.lat)
        assertThat(saved.lng).isEqualTo(iocl.lng)
    }

    @Test
    fun aTypedNameKeepsThePositionSoItIsLearnedThere() = runTest {
        val vm = openFillUp("r3")
        vm.onEditStation()
        vm.onTypeStation("  Raju Fuels ")
        val saved = entries.refuels.single { it.id == "r3" }
        assertThat(saved.stationName).isEqualTo("Raju Fuels")
        assertThat(saved.lat).isEqualTo(hereLat)
    }

    @Test
    fun removingTheLocationKeepsTheName() = runTest {
        val vm = openFillUp("r3")
        vm.onEditStation()
        vm.onRemoveLocation()
        val saved = entries.refuels.single { it.id == "r3" }
        assertThat(saved.lat).isNull()
        assertThat(saved.lng).isNull()
        assertThat(saved.stationName).isEqualTo("Shell")
    }

    @Test
    fun withoutAPositionOnlyANameCanBeTyped() = runTest {
        val vm = openFillUp("r1")
        vm.onEditStation()
        val edit = vm.stationEdit.value!!
        assertThat(edit.hasLocation).isFalse()
        assertThat(edit.loading).isFalse()
        assertThat(edit.options).isEmpty()
        vm.onDismissStation()
        assertThat(vm.stationEdit.value).isNull()
    }
}

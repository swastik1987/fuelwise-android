package app.fuelwise.feature.dataimport

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.domain.importing.EntryKind
import app.fuelwise.core.domain.importing.ImportField
import app.fuelwise.core.domain.importing.RowIssue
import app.fuelwise.core.model.ImportSource
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeImportRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ImportViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imports = FakeImportRepository()
    private val entries = FakeEntryRepository()

    private fun viewModel(
        vehicles: FakeVehicleRepository = FakeVehicleRepository(listOf(SampleVehicles.safari, SampleVehicles.ertiga)),
    ) = ImportViewModel(
        imports = imports,
        vehicles = vehicles,
        categories = FakeCategoryRepository(),
        entries = entries,
        userData = FakeUserDataRepository(),
        zoneId = ZoneId.of("Asia/Kolkata"),
        context = context,
        io = main.dispatcher,
        compute = main.dispatcher,
    )

    private fun file(name: String, text: String): Uri = Uri.fromFile(
        File(context.cacheDir, name).apply {
            writeText(text)
        },
    )

    @Test
    fun aSpreadsheetGoesThroughTheMapperThenImports() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val csv = "Vehicle,When,Odo,Litres,Rate,Mode\n" +
            "Safari,16/10/2026,42318,40.25,88.95,UPI\n" +
            "Swift,17/10/2026,42800,30,88.95,Cash\n"
        vm.pickFile(file("log.csv", csv))

        val mapper = vm.state.value.mapper!!
        assertThat(vm.state.value.step).isEqualTo(ImportStep.Mapping)
        assertThat(mapper.mapping.kind).isEqualTo(EntryKind.Fuel)
        assertThat(mapper.mapping[ImportField.Quantity]).isEqualTo(3)
        assertThat(mapper.samples[0]).isEqualTo("Safari")

        // "When" is not a known header: map it by hand.
        assertThat(mapper.mapping[ImportField.Date]).isNull()
        vm.setColumn(ImportField.Date, 1)
        vm.confirmMapping()

        val preview = vm.state.value.preview!!
        assertThat(vm.state.value.step).isEqualTo(ImportStep.Preview)
        assertThat(preview.source).isEqualTo(ImportSource.Spreadsheet)
        assertThat(preview.plan.refuels.single().paymentMethod).isEqualTo(PaymentMethod.Upi)
        assertThat(preview.plan.problems.single().issue).isEqualTo(RowIssue.VehicleNotFound)
        assertThat(preview.askVehicle).isFalse()

        val event = async { vm.events.first() }
        vm.confirmImport()
        assertThat(imports.committed.single().refuels).hasSize(1)
        assertThat((event.await() as ImportEvent.Imported).batch.entryCount).isEqualTo(1)
        assertThat(vm.state.value.step).isEqualTo(ImportStep.Start)
        assertThat(vm.state.value.batches).hasSize(1)

        // The same layout maps itself next time.
        vm.pickFile(file("log2.csv", csv))
        assertThat(vm.state.value.mapper!!.mapping[ImportField.Date]).isEqualTo(1)
    }

    @Test
    fun aFuelioExportSkipsTheMapperAndPicksItsVehicle() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val fuelio = """
            "## Vehicle"
            "Name","DistUnit","FuelUnit"
            "Ertiga","0","0"
            "## Log"
            "Data","Odo (km)","Fuel (litres)","Full","Price (optional)","VolumePrice","TankNumber"
            "2026-10-01 10:00","31100","8.5","1","765","90","1"
            "## CostCategories"
            "CostTypeID","Name"
            "2","Car wash"
            "## Costs"
            "CostTitle","Date","Odo","CostTypeID","Notes","Cost","isTemplate","isIncome"
            "Wash","2026-10-02","31120","2","","300","0","0"
        """.trimIndent()
        vm.pickFile(file("fuelio.csv", fuelio))
        val preview = vm.state.value.preview!!
        assertThat(preview.source).isEqualTo(ImportSource.Fuelio)
        assertThat(preview.fileName).isEqualTo("fuelio.csv")
        assertThat(preview.vehicleId).isEqualTo("ertiga")
        assertThat(preview.askVehicle).isTrue()
        assertThat(preview.plan.refuels.single().tankId).isEqualTo("ertiga-cng")
        assertThat(preview.plan.expenses.single().categoryId).isEqualTo(SystemCategory.Wash.id)

        vm.setVehicle("safari")
        assertThat(vm.state.value.preview!!.plan.expenses.single().vehicleId).isEqualTo("safari")
    }

    @Test
    fun theDownloadedTemplateComesBackRecognisedButEmpty() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val target = File(context.cacheDir, "template.xlsx")
        val saved = async { vm.events.first() }
        vm.saveTemplate(Uri.fromFile(target))
        assertThat((saved.await() as ImportEvent.TemplateSaved).ok).isTrue()
        assertThat(target.length()).isGreaterThan(0L)

        vm.pickFile(Uri.fromFile(target))
        assertThat(vm.state.value.step).isEqualTo(ImportStep.Start)
        assertThat(vm.state.value.error).isEqualTo(ImportError.Empty)
    }

    @Test
    fun withoutVehiclesNothingIsRead() = runTest(main.dispatcher) {
        val vm = viewModel(FakeVehicleRepository())
        backgroundScope.launch { vm.state.collect {} }
        vm.pickFile(file("x.csv", "Date,Amount\n16/10/2026,10\n"))
        assertThat(vm.state.value.error).isEqualTo(ImportError.NoVehicles)
    }

    @Test
    fun anUnreadableFileSaysSo() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        vm.pickFile(
            Uri.fromFile(
                File(context.cacheDir, "broken.xlsx").apply {
                    writeBytes(byteArrayOf(0x50, 0x4B, 3, 4, 9, 9))
                },
            ),
        )
        assertThat(vm.state.value.error).isEqualTo(ImportError.Unreadable)
    }
}

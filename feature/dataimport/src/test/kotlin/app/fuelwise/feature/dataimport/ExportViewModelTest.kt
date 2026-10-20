package app.fuelwise.feature.dataimport

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.repository.EntitlementRepository
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.report.BuildReports
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeDocumentRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeFastagRepository
import app.fuelwise.core.testing.FakeRecurrenceRepository
import app.fuelwise.core.testing.FakeReminderRepository
import app.fuelwise.core.testing.FakeReportPreferencesRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExportViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 19, 10, 0).atZone(zone).toInstant()
    private val rates = FakeReportPreferencesRepository()

    private fun viewModel(
        vehicles: FakeVehicleRepository = FakeVehicleRepository(listOf(SampleVehicles.safari)),
        entries: FakeEntryRepository =
            FakeEntryRepository(SampleEntries.safariRefuels, listOf(SampleEntries.safariService)),
        trips: FakeTripRepository = FakeTripRepository(),
    ) = ExportViewModel(
        vehicles = vehicles,
        categories = FakeCategoryRepository(),
        entries = entries,
        trips = trips,
        reminders = FakeReminderRepository(),
        documents = FakeDocumentRepository(),
        fastag = FakeFastagRepository(),
        recurrences = FakeRecurrenceRepository(),
        reportPreferences = rates,
        entitlements = object : EntitlementRepository {
            override val isPro = flowOf(false)
        },
        buildReports = BuildReports(ComputeCostMetrics(), ComputeConsumption()),
        clock = Clock.fixed(Instant.from(now), zone),
        zoneId = zone,
        context = context,
        io = main.dispatcher,
        compute = main.dispatcher,
    )

    @Test
    fun exportAsksForAFileThenWritesTheZip() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val events = mutableListOf<ExportEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.startExport()
        val pick = events.single() as ExportEvent.PickExportFile
        assertThat(pick.fileName).isEqualTo("fuelwise-export-2026-10-19.zip")

        val out = File(context.cacheDir, "export.zip")
        vm.writeExport(Uri.fromFile(out))
        assertThat(events.last()).isInstanceOf(ExportEvent.Exported::class.java)
        val names = ZipFile(out).use { zip -> zip.entries().toList().map { it.name } }
        assertThat(names).containsAtLeast("vehicles.csv", "refuels.csv", "expenses.csv")
        assertThat(vm.state.value.exporting).isFalse()
        assertThat(vm.state.value.problem).isNull()
    }

    @Test
    fun aRangeWithNothingInItSaysSoWithoutOpeningThePicker() = runTest(main.dispatcher) {
        val vm = viewModel(entries = FakeEntryRepository())
        backgroundScope.launch { vm.state.collect {} }
        val events = mutableListOf<ExportEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.setRange(DataRange.LastMonth)
        vm.startExport()
        assertThat(events).isEmpty()
        assertThat(vm.state.value.problem).isEqualTo(ExportProblem.NothingToExport)
    }

    @Test
    fun cancellingThePickerWritesNothing() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.events.collect {} }
        vm.startExport()
        vm.writeExport(null)
        assertThat(vm.state.value.exporting).isFalse()
        assertThat(vm.state.value.problem).isNull()
    }

    @Test
    fun vehicleReportDefaultsToTheFirstVehicle() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        assertThat(vm.state.value.reportVehicleId).isEqualTo("safari")
        assertThat(vm.state.value.report).isInstanceOf(Report.Vehicle::class.java)
        vm.setReportKind(ReportKind.Seller)
        assertThat(vm.state.value.report).isInstanceOf(Report.Seller::class.java)
    }

    @Test
    fun businessReportWaitsForARateAndRemembersIt() = runTest(main.dispatcher) {
        val trip = Trip(
            "t1",
            "safari",
            LocalDateTime.of(2026, 10, 6, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            zone.id,
            42.5,
            TripPurpose.Business,
        )
        val vm = viewModel(trips = FakeTripRepository(listOf(trip)))
        backgroundScope.launch { vm.state.collect {} }
        vm.setReportKind(ReportKind.Business)
        assertThat(vm.state.value.report).isNull()

        vm.setRate("10.50")
        assertThat(rates.businessRatePerKm.first()).isEqualTo(Money(1_050))
        val report = (vm.state.value.report as Report.Business).report
        assertThat(report.totalAmount).isEqualTo(Money(44_625))
    }

    @Test
    fun theNextPeriodStopsAtThisMonth() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        assertThat(vm.state.value.canGoForward).isFalse()
        vm.stepPeriod(forward = false)
        assertThat(vm.state.value.month.monthValue).isEqualTo(9)
        vm.stepPeriod(forward = true)
        vm.stepPeriod(forward = true)
        assertThat(vm.state.value.month.monthValue).isEqualTo(10)
    }
}

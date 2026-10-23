package app.fuelwise.feature.garage.list

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.testing.FakeBackupSettingsRepository
import app.fuelwise.core.testing.FakeDocumentRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeFastagRepository
import app.fuelwise.core.testing.FakeReminderRepository
import app.fuelwise.core.testing.FakeVehiclePhotoRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.core.testing.reminderStatuses
import app.fuelwise.feature.garage.PENDING_DELETE_VEHICLE_ID
import app.fuelwise.feature.garage.photo.VehiclePhotoLoader
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GarageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val vehicles = FakeVehicleRepository(SampleVehicles.all)
    private val handle = SavedStateHandle()
    private val clock = Clock.fixed(Instant.parse("2026-10-16T04:30:00Z"), ZoneOffset.UTC)
    private val entries = FakeEntryRepository()
    private val reminders = FakeReminderRepository(
        listOf(
            Reminder(
                "r1",
                "safari",
                ReminderKind.Insurance,
                null,
                Instant.parse("2026-10-20T00:00:00Z").toEpochMilli(),
                null,
            ),
        ),
    )
    private val documents = FakeDocumentRepository(
        listOf(
            Document(
                "d1",
                "safari",
                DocumentType.Puc,
                expiryDateUtcMs = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(),
            ),
        ),
    )
    private val viewModel = GarageViewModel(
        vehicles,
        handle,
        reminderStatuses(reminders, vehicles, entries, clock),
        documents,
        FakeFastagRepository(),
        entries,
        FakeBackupSettingsRepository(),
        VehiclePhotoLoader(FakeVehiclePhotoRepository(), main.dispatcher),
        clock,
    )

    @Test
    fun countsRemindersDueAndDocumentsExpiring() = runTest {
        val ready = viewModel.uiState.first { it is GarageUiState.Ready } as GarageUiState.Ready
        assertThat(ready.remindersDue).isEqualTo(1)
        assertThat(ready.documentsExpiring).isEqualTo(1)
        assertThat(ready.fastagEstimate).isNull()
    }

    @Test
    fun splitsActiveAndArchivedVehicles() = runTest {
        viewModel.uiState.test {
            val ready = awaitItem().let { if (it is GarageUiState.Loading) awaitItem() else it } as GarageUiState.Ready
            assertThat(ready.active.map { it.name }).containsExactly("Safari", "Ertiga", "Nexon EV").inOrder()
            assertThat(ready.archived.map { it.name }).containsExactly("Activa")
        }
    }

    @Test
    fun pendingDeleteFromDetailDeletesAndOffersUndo() = runTest {
        viewModel.events.test {
            handle[PENDING_DELETE_VEHICLE_ID] = SampleVehicles.ertiga.id
            val event = awaitItem() as GarageEvent.VehicleDeleted
            assertThat(event.name).isEqualTo("Ertiga")
            assertThat(vehicles.observeVehicle(SampleVehicles.ertiga.id).first()).isNull()

            viewModel.undoDelete(event.token)
            assertThat(vehicles.observeVehicle(SampleVehicles.ertiga.id).first()).isNotNull()
        }
    }
}

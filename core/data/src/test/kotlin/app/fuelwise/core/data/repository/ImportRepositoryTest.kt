package app.fuelwise.core.data.repository

import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.UserPreferencesSerializer
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.core.domain.importing.ColumnMapping
import app.fuelwise.core.domain.importing.EntryKind
import app.fuelwise.core.domain.importing.ImportField
import app.fuelwise.core.domain.importing.ImportPlan
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.ImportSource
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ImportRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-16T04:30:00Z"), ZoneOffset.UTC)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FuelwiseDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val ids = UuidV7Generator(clock)
    private val entries = OfflineEntryRepository(db.entryDao(), ids, clock) { "device-a" }
    private val dir: File = Files.createTempDirectory("import-prefs").toFile()

    @After
    fun tearDown() = db.close()

    private fun kotlinx.coroutines.test.TestScope.repository() = OfflineImportRepository(
        db.importDao(),
        ids,
        SyncStamper(clock) { "device-a" },
        PreferencesDataSource(
            DataStoreFactory.create(UserPreferencesSerializer(), scope = backgroundScope) { File(dir, "prefs.pb") },
            ids,
        ),
    )

    private val plan = ImportPlan(
        refuels = listOf(
            Refuel("", "v1", "t1", 1_000, "Asia/Kolkata", 42_318.0, 40.25, Money(8_895), Money(358_024), isFull = true),
            Refuel("", "v1", "t1", 2_000, "Asia/Kolkata", 42_800.0, 38.0, Money(8_895), Money(338_010), isFull = true),
        ),
        expenses = listOf(Expense("", "v1", SystemCategory.Service.id, 1_500, "Asia/Kolkata", Money(485_000))),
        duplicates = 0,
        problems = emptyList(),
        otherCategoryRows = 0,
    )

    @Test
    fun commitSavesEverythingUnderOneBatchAndUndoRemovesIt() = runTest {
        val imports = repository()
        val batch = imports.commit(plan, ImportSource.Fuelio, "fuelio.csv")
        assertThat(batch.entryCount).isEqualTo(3)
        assertThat(entries.observeRefuels("v1").first()).hasSize(2)
        assertThat(entries.observeExpenses("v1").first()).hasSize(1)
        assertThat(imports.observeBatches().first().single().source).isEqualTo(ImportSource.Fuelio)

        // An entry edited after the import still belongs to it.
        val edited = entries.observeRefuels("v1").first().first()
        entries.saveRefuel(edited.copy(notes = "Fixed the odometer"))

        imports.undo(batch.id)
        assertThat(entries.observeRefuels("v1").first()).isEmpty()
        assertThat(entries.observeExpenses("v1").first()).isEmpty()
        assertThat(imports.observeBatches().first()).isEmpty()
    }

    @Test
    fun undoLeavesEntriesFromOtherSourcesAlone() = runTest {
        val imports = repository()
        val typed = entries.saveRefuel(plan.refuels.first().copy(odometerKm = 41_000.0))
        val batch = imports.commit(plan, ImportSource.Template, null)
        imports.undo(batch.id)
        assertThat(entries.observeRefuels("v1").first().map { it.id }).containsExactly(typed)
    }

    @Test
    fun mappingsAreRemembered() = runTest {
        val imports = repository()
        val mapping = ColumnMapping(EntryKind.Fuel, mapOf(ImportField.Date to 0, ImportField.Odometer to 2))
        val signature = ColumnMapping.signature(listOf("Date", "Notes", "Odo"))
        assertThat(imports.savedMapping(signature)).isNull()
        imports.saveMapping(signature, mapping)
        assertThat(imports.savedMapping(signature)).isEqualTo(mapping)
    }
}

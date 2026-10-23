package app.fuelwise.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.database.model.CategoryEntity
import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.SystemCategory
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Custom categories, renames, hiding and deletes (PRD F-04.2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineCategoryRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-23T04:30:00Z"), ZoneOffset.UTC)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FuelwiseDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val ids = UuidV7Generator(clock)
    private val categories = OfflineCategoryRepository(db.categoryDao(), ids, SyncStamper(clock) { "device-a" })
    private val entries = OfflineEntryRepository(db.entryDao(), ids, clock) { "device-a" }

    @After
    fun tearDown() = db.close()

    private suspend fun seedService() = db.categoryDao().upsert(
        CategoryEntity(
            id = SystemCategory.Service.id,
            systemKey = SystemCategory.Service.name,
            customName = null,
            icon = null,
            color = null,
            isHidden = false,
            includeInCostPerKm = true,
            sortOrder = 0,
            sync = SyncMeta(0, 0, "seed"),
        ),
    )

    @Test
    fun customCategoriesAreAddedAfterThePresets() = runTest {
        seedService()
        val id = categories.addCustom("  Driver salary  ", "person")
        val added = categories.observeCategories().first().last()
        assertThat(added.id).isEqualTo(id)
        assertThat(added.customName).isEqualTo("Driver salary")
        assertThat(added.icon).isEqualTo("person")
        assertThat(added.isSystem).isFalse()
        assertThat(added.sortOrder).isEqualTo(1)
    }

    @Test
    fun presetsRenameAndResetButNeverDelete() = runTest {
        seedService()
        categories.rename(SystemCategory.Service.id, "Car service")
        assertThat(categories.observeCategories().first().single().customName).isEqualTo("Car service")
        // A blank name puts the preset back to its translated name.
        categories.rename(SystemCategory.Service.id, null)
        assertThat(categories.observeCategories().first().single().customName).isNull()
        assertThat(categories.remove(SystemCategory.Service.id)).isEqualTo(CategoryRemoval.NotAllowed)
        categories.setHidden(SystemCategory.Service.id, true)
        assertThat(categories.observeCategories().first().single().isHidden).isTrue()
    }

    @Test
    fun aUsedCustomCategoryIsHiddenAndAnUnusedOneTombstoned() = runTest {
        val used = categories.addCustom("Driver salary", "person")
        val unused = categories.addCustom("Toll pass", "card")
        entries.saveExpense(Expense("", "safari", used, clock.millis(), "Asia/Kolkata", Money(1_200_000)))
        // A custom category keeps a name.
        categories.rename(used, "  ")
        assertThat(categories.observeCategories().first().first { it.id == used }.customName).isEqualTo("Driver salary")

        assertThat(categories.remove(used)).isEqualTo(CategoryRemoval.HiddenBecauseUsed)
        assertThat(categories.remove(unused)).isEqualTo(CategoryRemoval.Deleted)
        assertThat(categories.observeCategories().first().map { it.id to it.isHidden }).containsExactly(used to true)
        // A tombstone, not a hard delete (CLAUDE.md rule 3).
        val row = db.query("SELECT deletedAt FROM category WHERE id = ?", arrayOf(unused))
        row.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(0)).isEqualTo(clock.millis())
        }
    }
}

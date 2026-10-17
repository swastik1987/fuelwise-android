package app.fuelwise.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration harness (CLAUDE.md rule 9). For every new version N: add `MIGRATION_{N-1}_N` to DatabaseModule and a
 * test here that creates version N-1 with real rows, runs the migration, validates the schema and checks the rows.
 * Run with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FuelwiseDatabase::class.java)

    @Test
    fun version1SchemaCreatesAndSeeds() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            FuelwiseDatabase.SEED_CALLBACK.onCreate(db)
            db.query("SELECT COUNT(*) FROM category").use { cursor ->
                cursor.moveToFirst()
                check(cursor.getInt(0) == SystemCategorySeed.rows.size)
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}

package app.fuelwise.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fuelwise.core.database.migration.Migrations
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every migration, run against real rows of the previous version and validated against the exported schema
 * (CLAUDE.md rule 9). Add a test here with each new version.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationsTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FuelwiseDatabase::class.java)

    @Test
    fun migrate1To2KeepsVehiclesAndAddsEntryTables() {
        helper.createDatabase(dbPath, 1).use { db ->
            FuelwiseDatabase.SEED_CALLBACK.onCreate(db)
            db.execSQL(
                """
                INSERT INTO vehicle (id, name, type, isHybrid, archived, sortOrder, startOdometerKm, startOdometerAt,
                    startOdometerZoneId, createdAt, updatedAt, deviceId)
                VALUES ('safari', 'Safari', 'Car', 0, 0, 0, 42318.0, 1, 'Asia/Kolkata', 1, 1, 'd1')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(dbPath, 2, true, Migrations.MIGRATION_1_2).use { db ->
            db.query("SELECT name FROM vehicle WHERE id = 'safari'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Safari")
            }
            db.query("SELECT COUNT(*) FROM category").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(SystemCategorySeed.rows.size)
            }
            db.execSQL(
                """
                INSERT INTO refuel (id, vehicleId, tankId, dateTime, zoneId, odometerKm, quantity, unitPricePaise,
                    totalPaise, discountPaise, paidPaise, isFull, missedPrevious, priceSource, createdAt, updatedAt, deviceId)
                VALUES ('r1', 'safari', 't1', 2, 'Asia/Kolkata', 42800.0, 40.0, 8895, 355800, 0, 355800, 1, 0, 'Typed', 2, 2, 'd1')
                """.trimIndent(),
            )
        }
    }

    // An absolute path: on Windows the helper otherwise compares a bare name with the resolved path and refuses.
    private val dbPath: String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(DB).absolutePath

    private companion object {
        const val DB = "migrations-test"
    }
}

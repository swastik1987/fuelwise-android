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

    @Test
    fun migrate2To3KeepsEntriesAndAddsCareTables() {
        helper.createDatabase(dbPath, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO expense (id, vehicleId, categoryId, dateTime, zoneId, amountPaise, isAutoCreated,
                    createdAt, updatedAt, deviceId)
                VALUES ('e1', 'safari', 'c1', 5, 'Asia/Kolkata', 485000, 0, 5, 5, 'd1')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(dbPath, 3, true, Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT amountPaise FROM expense WHERE id = 'e1'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getLong(0)).isEqualTo(485_000L)
            }
            val auto = """
                INSERT OR IGNORE INTO expense (id, vehicleId, categoryId, dateTime, zoneId, amountPaise, recurrenceId,
                    isAutoCreated, createdAt, updatedAt, deviceId)
                VALUES (?, 'safari', 'c1', 100, 'Asia/Kolkata', 1200000, 'emi', 1, 6, 6, 'd1')
            """.trimIndent()
            db.execSQL(auto, arrayOf<Any>("a1"))
            db.execSQL(auto, arrayOf<Any>("a2"))
            db.query("SELECT COUNT(*) FROM expense WHERE recurrenceId = 'emi'").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
        }
    }

    @Test
    fun migrate3To4KeepsEntriesAndAddsImportBatches() {
        helper.createDatabase(dbPath, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO refuel (id, vehicleId, tankId, dateTime, zoneId, odometerKm, quantity, unitPricePaise,
                    totalPaise, discountPaise, paidPaise, isFull, missedPrevious, priceSource, createdAt, updatedAt, deviceId)
                VALUES ('r1', 'safari', 't1', 2, 'Asia/Kolkata', 42800.0, 40.0, 8895, 355800, 0, 355800, 1, 0, 'Typed', 2, 2, 'd1')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(dbPath, 4, true, Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT paidPaise, importBatchId FROM refuel WHERE id = 'r1'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getLong(0)).isEqualTo(355_800L)
                assertThat(c.isNull(1)).isTrue()
            }
            db.execSQL(
                """
                INSERT INTO import_batch (id, source, fileName, importedAt, refuelCount, expenseCount,
                    createdAt, updatedAt, deviceId)
                VALUES ('b1', 'Fuelio', 'fuelio.csv', 7, 1, 0, 7, 7, 'd1')
                """.trimIndent(),
            )
            db.execSQL("UPDATE refuel SET importBatchId = 'b1' WHERE id = 'r1'")
            db.query("SELECT COUNT(*) FROM refuel WHERE importBatchId = 'b1'").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
        }
    }

    @Test
    fun migrate4To5KeepsTripLinkedExpensesAndAddsTrips() {
        helper.createDatabase(dbPath, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO expense (id, vehicleId, categoryId, dateTime, zoneId, amountPaise, tripId, tollPlaza,
                    isAutoCreated, createdAt, updatedAt, deviceId)
                VALUES ('e1', 'safari', 'c1', 5, 'Asia/Kolkata', 23000, 'trip1', 'Kaniminike', 0, 5, 5, 'd1')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(dbPath, 5, true, Migrations.MIGRATION_4_5).use { db ->
            db.execSQL(
                """
                INSERT INTO trip (id, vehicleId, startTime, zoneId, startOdo, endOdo, distanceKm, purpose, estCostPaise,
                    source, status, createdAt, updatedAt, deviceId)
                VALUES ('trip1', 'safari', 9, 'Asia/Kolkata', 42318.0, 42464.0, 146.0, 'Personal', 89600,
                    'Manual', 'Done', 9, 9, 'd1')
                """.trimIndent(),
            )
            db.query(
                "SELECT t.distanceKm, SUM(e.amountPaise) FROM trip t JOIN expense e ON e.tripId = t.id GROUP BY t.id",
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getDouble(0)).isEqualTo(146.0)
                assertThat(c.getLong(1)).isEqualTo(23_000L)
            }
        }
    }

    // An absolute path: on Windows the helper otherwise compares a bare name with the resolved path and refuses.
    private val dbPath: String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(DB).absolutePath

    private companion object {
        const val DB = "migrations-test"
    }
}

package app.fuelwise.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.mapper.toEntity
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineTripRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-16T04:30:00Z"), ZoneOffset.UTC)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FuelwiseDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val ids = UuidV7Generator(clock)
    private val trips = OfflineTripRepository(db.tripDao(), ids, SyncStamper(clock) { "device-a" })
    private val entries = OfflineEntryRepository(db.entryDao(), ids, clock) { "device-a" }

    @After
    fun tearDown() = db.close()

    @Test
    fun saveEditDeleteAndUndo() = runTest {
        val id = trips.save(trip())
        trips.save(trips.observeTrip(id).first()!!.copy(purpose = TripPurpose.Business, notes = "Client visit"))
        val saved = trips.observeTrips().first().single()
        assertThat(saved.id).isEqualTo(id)
        assertThat(saved.purpose).isEqualTo(TripPurpose.Business)
        assertThat(saved.estimatedFuelCost).isEqualTo(Money(89_600))

        val token = trips.delete(id)
        assertThat(trips.observeTrips().first()).isEmpty()
        // A tombstone, not a hard delete (CLAUDE.md rule 3).
        assertThat(db.tripDao().trip(id)?.sync?.deletedAt).isEqualTo(clock.millis())
        trips.undoDelete(token)
        assertThat(trips.observeTrips().first()).hasSize(1)
    }

    @Test
    fun editKeepsTheRecordedRoute() = runTest {
        db.tripDao().upsert(trip().copy(id = "gps").toEntity(SyncStamper(clock) { "d" }.stamp(), "route-1"))
        trips.save(trips.observeTrip("gps").first()!!.copy(notes = "Edited"))
        assertThat(db.tripDao().trip("gps")?.polylineAttachmentId).isEqualTo("route-1")
    }

    @Test
    fun expenseKeepsItsTripLink() = runTest {
        val expenseId = entries.saveExpense(
            Expense(
                "",
                "safari",
                SystemCategory.TollFastag.id,
                clock.millis(),
                "Asia/Kolkata",
                Money(23_000),
                tollPlaza = "Kaniminike",
                tripId = "trip-1",
            ),
        )
        assertThat(entries.observeExpense(expenseId).first()?.tripId).isEqualTo("trip-1")
    }

    private fun trip() = Trip(
        id = "",
        vehicleId = "safari",
        startUtcMs = clock.millis(),
        zoneId = "Asia/Kolkata",
        distanceKm = 146.0,
        purpose = TripPurpose.Personal,
        startOdometerKm = 42_318.0,
        endOdometerKm = 42_464.0,
        fromPlace = "Bengaluru",
        toPlace = "Mysuru",
        estimatedFuelCost = Money(89_600),
    )
}

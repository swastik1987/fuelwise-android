package app.fuelwise.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.mapper.toEntity
import app.fuelwise.core.data.vault.AttachmentStore
import app.fuelwise.core.data.vault.VaultCipher
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
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
    private val files = mutableMapOf<String, ByteArray>()
    private val store = object : AttachmentStore {
        override suspend fun write(attachmentId: String, plaintext: ByteArray): String =
            "$attachmentId.bin".also { files[it] = plaintext.reversedArray() }

        override suspend fun read(attachmentId: String, storedName: String) =
            files[storedName]?.reversedArray() ?: throw java.io.FileNotFoundException(storedName)

        override suspend fun delete(storedName: String) {
            files.remove(storedName)
        }
    }

    // Reverses and tags the bytes: enough to prove what's stored isn't the plain encoding, and that AD is checked.
    private val cipher = object : VaultCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) =
            associatedData + byteArrayOf(0) + plaintext.reversedArray()

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            check(ciphertext.take(associatedData.size) == associatedData.toList()) { "Wrong associated data" }
            return ciphertext.drop(associatedData.size + 1).toByteArray().reversedArray()
        }
    }
    private val trips = OfflineTripRepository(
        db.tripDao(),
        db.documentDao(),
        store,
        cipher,
        ids,
        SyncStamper(clock) { "device-a" },
        Dispatchers.Unconfined,
    )
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

    @Test
    fun recordingKeepsRawPointsEncryptedThenOnlyTheRoute() = runTest {
        val id = trips.save(trip().copy(source = TripSource.Gps, status = TripStatus.Recording))
        assertThat(trips.observeUnfinished().first().map { it.id }).containsExactly(id)

        val first = listOf(point(0, 12.900), point(5, 12.901))
        val second = listOf(point(10, 12.902, segment = 1))
        trips.appendPoints(id, first)
        trips.appendPoints(id, second)
        trips.appendPoints(id, emptyList())
        assertThat(trips.points(id)).isEqualTo(first + second)
        // Stored as ciphertext bound to (trip, seq), never as the plain encoding.
        val stored = db.tripDao().batches(id)
        assertThat(stored.map { it.seq }).containsExactly(0, 1).inOrder()
        assertThat(stored.first().payload.decodeToString()).startsWith("$id:0")

        val done = trips.observeTrip(id).first()!!
            .copy(status = TripStatus.Done, distanceKm = 0.2, recordedSeconds = 10)
        trips.finishRecording(done, listOf(first.first(), second.last()))
        assertThat(db.tripDao().batches(id)).isEmpty()
        assertThat(trips.route(id)).containsExactly(first.first(), second.last()).inOrder()
        assertThat(trips.observeUnfinished().first()).isEmpty()
        assertThat(trips.observeTrip(id).first()?.recordedSeconds).isEqualTo(10)
    }

    @Test
    fun discardDropsTheTripAndItsPoints() = runTest {
        val id = trips.save(trip().copy(source = TripSource.Gps, status = TripStatus.Recording))
        trips.appendPoints(id, listOf(point(0, 12.9)))
        trips.discardRecording(id)
        assertThat(db.tripDao().batches(id)).isEmpty()
        assertThat(trips.observeTrips().first()).isEmpty()
        assertThat(trips.route(id)).isEmpty()
    }

    private fun point(seconds: Long, lat: Double, segment: Int = 0) =
        TrackPoint(seconds * 1_000, lat, 77.6, 8f, 12f, segment)

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

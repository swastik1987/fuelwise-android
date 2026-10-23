package app.fuelwise.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.vault.AttachmentStore
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.core.domain.reminder.PresetRule
import app.fuelwise.core.domain.reminder.ReminderRules
import app.fuelwise.core.domain.vehicle.VehicleDraft
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Recurrence
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.RepeatUnit
import app.fuelwise.core.model.SystemCategory
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CareRepositoriesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 16).atTime(9, 30).atZone(zone).toInstant(), zone)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FuelwiseDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val ids = UuidV7Generator(clock)
    private val sync = SyncStamper(clock) { "device-a" }
    private val vehicles = OfflineVehicleRepository(db.vehicleDao(), ids, clock, { "device-a" }, zone)
    private val reminders = OfflineReminderRepository(db.reminderDao(), ids, sync, zone)
    private val recurrences = OfflineRecurrenceRepository(db.recurrenceDao(), ids, sync)
    private val fastag = OfflineFastagRepository(db.fastagDao(), ids, sync)
    private val files = mutableMapOf<String, ByteArray>()
    private val store = object : AttachmentStore {
        override suspend fun write(attachmentId: String, plaintext: ByteArray): String =
            "$attachmentId.bin".also { files[it] = plaintext.reversedArray() }

        override suspend fun read(attachmentId: String, storedName: String) = files.getValue(storedName).reversedArray()

        override suspend fun delete(storedName: String) {
            files.remove(storedName)
        }

        override suspend fun deleteAll() = files.clear()
    }
    private val rules = object : RulesRepository {
        override suspend fun reminderRules() = ReminderRules(
            version = 1,
            presets = mapOf(ReminderKind.Insurance to PresetRule(notifyDaysBefore = listOf(30, 15, 7, 1))),
            pucMonthsByNorm = emptyMap(),
            pucDefault = PresetRule(months = 6),
            odometerWindowKmCar = 300.0,
            odometerWindowKmTwoWheeler = 150.0,
        )
    }
    private val documents =
        OfflineDocumentRepository(db.documentDao(), db.reminderDao(), reminders, vehicles, rules, store, ids, sync)

    @After
    fun tearDown() = db.close()

    @Test
    fun aVehicleKeepsOnePhotoAndANewOneReplacesIt() = runTest {
        val photos = OfflineVehiclePhotoRepository(db.documentDao(), documents)
        photos.setPhoto("v1", byteArrayOf(1, 2))
        photos.setPhoto("v1", byteArrayOf(3, 4, 5))
        photos.setPhoto("v2", byteArrayOf(9))
        val all = photos.observePhotos().first()
        assertThat(all.keys).containsExactly("v1", "v2")
        assertThat(
            photos.read(all.getValue("v1")).toList(),
        ).containsExactly(3.toByte(), 4.toByte(), 5.toByte()).inOrder()
        // The replaced file is gone; its row is a tombstone (CLAUDE.md rule 3).
        assertThat(files).hasSize(2)
        photos.removePhoto("v1")
        assertThat(photos.observePhotos().first().keys).containsExactly("v2")
        // Document attachments aren't vehicle photos.
        documents.addAttachment("v2", Attachment.OwnerType.Document, byteArrayOf(7), "image/jpeg")
        assertThat(photos.read(photos.observePhotos().first().getValue("v2")).toList()).containsExactly(9.toByte())
    }

    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun recurringExpensesAreCreatedOncePerDueDate() = runTest {
        recurrences.save(
            Recurrence(
                "",
                "v1",
                SystemCategory.LoanEmi.id,
                Money(1_200_000),
                1,
                RepeatUnit.Months,
                date(2026, 8, 5),
                zone.id,
            ),
        )
        assertThat(recurrences.createDueEntries()).isEqualTo(3)
        // Running again, as after a restore or a second worker run, creates nothing.
        assertThat(recurrences.createDueEntries()).isEqualTo(0)
        val auto = recurrences.observeUnconfirmed().first()
        assertThat(
            auto.map {
                it.dateTimeUtcMs
            },
        ).containsExactly(date(2026, 10, 5), date(2026, 9, 5), date(2026, 8, 5)).inOrder()
        recurrences.confirm(auto.first().id)
        assertThat(recurrences.observeUnconfirmed().first()).hasSize(2)
    }

    @Test
    fun aDeletedAutoEntryIsNotRecreated() = runTest {
        recurrences.save(
            Recurrence(
                "",
                "v1",
                SystemCategory.Parking.id,
                Money(150_000),
                1,
                RepeatUnit.Months,
                date(2026, 10, 1),
                zone.id,
            ),
        )
        recurrences.createDueEntries()
        val entry = recurrences.observeUnconfirmed().first().single()
        db.entryDao().tombstoneExpense(entry.id, clock.millis(), "device-a")
        assertThat(recurrences.createDueEntries()).isEqualTo(0)
    }

    @Test
    fun completingARecurringReminderMovesItForward() = runTest {
        val id = reminders.save(
            Reminder(
                "",
                "v1",
                ReminderKind.Service,
                null,
                date(2026, 10, 20),
                24_100.0,
                repeatMonths = 12,
                repeatKm = 10_000.0,
            ),
        )
        reminders.complete(id, odometerKm = 24_050.0)
        val moved = reminders.reminder(id)!!
        assertThat(moved.dueDateUtcMs).isEqualTo(date(2027, 10, 16))
        assertThat(moved.dueOdometerKm).isEqualTo(34_050.0)
        assertThat(reminders.observeCompletions().first().single().reminderId).isEqualTo(id)
    }

    @Test
    fun completingAOneOffRetiresIt() = runTest {
        val id = reminders.save(Reminder("", "v1", ReminderKind.Custom, "Fix dent", date(2026, 10, 20), null))
        reminders.complete(id, odometerKm = null)
        assertThat(reminders.observeReminders().first()).isEmpty()
        assertThat(reminders.observeCompletions().first()).hasSize(1)
    }

    @Test
    fun documentExpiryKeepsAReminderInStep() = runTest {
        val vehicleId = vehicles.add(VehicleDraft(name = "Swift", fuelSetup = FuelSetup.Petrol, odometerKm = 20_000.0))
        val docId = documents.save(
            Document("", vehicleId, DocumentType.Insurance, number = "OG-27-1234", expiryDateUtcMs = date(2027, 3, 1)),
        )
        val reminder = reminders.observeReminders().first().single()
        assertThat(reminder.kind).isEqualTo(ReminderKind.Insurance)
        assertThat(reminder.documentId).isEqualTo(docId)
        assertThat(reminder.notifyDaysBefore).containsExactly(30, 15, 7, 1)

        val doc = documents.observeDocument(docId).first()!!
        documents.save(doc.copy(expiryDateUtcMs = date(2028, 3, 1)))
        assertThat(reminders.observeReminders().first().single().dueDateUtcMs).isEqualTo(date(2028, 3, 1))

        documents.save(doc.copy(remindOnExpiry = false))
        assertThat(reminders.observeReminders().first()).isEmpty()
    }

    @Test
    fun attachmentsAreStoredThroughTheVaultAndRemovedWithTheDocument() = runTest {
        val docId = documents.save(Document("", null, DocumentType.Licence))
        documents.addAttachment(docId, Attachment.OwnerType.Document, byteArrayOf(1, 2, 3), "image/jpeg")
        val attachment = documents.observeAttachments(docId).first().single()
        assertThat(
            documents.readAttachment(attachment).toList(),
        ).containsExactly(1.toByte(), 2.toByte(), 3.toByte()).inOrder()
        assertThat(attachment.sha256).hasLength(64)
        documents.delete(docId)
        assertThat(files).isEmpty()
        assertThat(documents.observeDocuments().first()).isEmpty()
    }

    @Test
    fun fastagKeepsOnlyTheLastFourDigits() = runTest {
        val id = fastag.saveAccount(FastagAccount("", "v1", "ICICI", "34161 2345 6789", Money(50_000), clock.millis()))
        assertThat(fastag.observeAccounts().first().single { it.id == id }.tagLast4).isEqualTo("6789")
    }

    @Test
    fun rulesFileParses() {
        val json = ApplicationProvider.getApplicationContext<android.content.Context>().assets.open("rules/india.json")
            .bufferedReader().use { it.readText() }
        val parsed = parseRules(json)
        assertThat(parsed.version).isEqualTo(1)
        assertThat(parsed.pucDefault.months).isEqualTo(6)
        assertThat(parsed.presets.getValue(ReminderKind.Service).twoWheeler?.km).isEqualTo(3_000.0)
        assertThat(Instant.ofEpochMilli(clock.millis())).isNotNull()
    }
}

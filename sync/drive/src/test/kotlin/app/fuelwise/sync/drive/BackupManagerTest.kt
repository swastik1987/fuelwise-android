package app.fuelwise.sync.drive

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.database.DatabaseSnapshotter
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.BackupError
import app.fuelwise.core.model.BackupSettings
import app.fuelwise.core.testing.FakeBackupSettingsRepository
import app.fuelwise.core.testing.FakeDocumentRepository
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End to end against a fake Drive (MockWebServer) and a real Room database: backup, restore on a "new device",
 * encryption, and atomic failure.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupManagerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-10-16T04:30:00Z"), zone)
    private val server = MockWebServer()
    private val drive = FakeDrive()

    private lateinit var db: FuelwiseDatabase
    private val settings = FakeBackupSettingsRepository(BackupSettings(accountEmail = "user@example.com"))
    private val keys = MemoryKeyStore()
    private val auth = object : DriveAuth {
        override val isConfigured = true

        override suspend fun signIn(activityContext: Context) = "user@example.com"

        override suspend fun authorize(email: String): DriveAccess = DriveAccess.Granted("token")

        override fun complete(data: Intent?): DriveAccess = DriveAccess.Granted("token")
    }

    private fun manager(database: FuelwiseDatabase = db, documents: FakeDocumentRepository = FakeDocumentRepository()) =
        BackupManager(
            snapshotter = DatabaseSnapshotter(database),
            settings = settings,
            deviceIds = { "device-a" },
            documents = documents,
            drive = DriveClient(server.url("").toString().trimEnd('/'), Dispatchers.Unconfined),
            auth = auth,
            keys = keys,
            clock = clock,
            zoneId = zone,
            io = Dispatchers.Unconfined,
        )

    private fun newDb() =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuelwiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Before
    fun setUp() {
        server.dispatcher = drive
        server.start()
        db = newDb()
        db.openHelper.writableDatabase.apply {
            execSQL(
                "INSERT INTO vehicle (id, name, type, isHybrid, archived, sortOrder, startOdometerKm, " +
                    "startOdometerAt, startOdometerZoneId, createdAt, updatedAt, deviceId) " +
                    "VALUES ('v1', 'Safari', 'Car', 0, 0, 0, 42000.0, 1, 'Asia/Kolkata', 1, 1, 'd1')",
            )
            execSQL(
                "INSERT INTO refuel (id, vehicleId, tankId, dateTime, zoneId, odometerKm, quantity, " +
                    "unitPricePaise, totalPaise, discountPaise, paidPaise, isFull, missedPrevious, priceSource, " +
                    "createdAt, updatedAt, deviceId) VALUES ('r1', 'v1', 't1', 2, 'Asia/Kolkata', 42318.5, 40.25, " +
                    "8895, 358024, 0, 358024, 1, 0, 'Typed', 2, 2, 'd1')",
            )
            execSQL(
                "INSERT INTO document (id, vehicleId, type, number, remindOnExpiry, createdAt, updatedAt, deviceId) " +
                    "VALUES ('doc1', 'v1', 'Insurance', 'OG-27-1', 1, 3, 3, 'd1')",
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun backupThenRestoreOnANewDeviceGivesTheSameRows() = runTest {
        val outcome = manager().backupNow()
        assertThat(outcome).isInstanceOf(BackupOutcome.Success::class.java)
        val file = drive.files.values.single()
        assertThat(file.name).startsWith("snapshot-")
        assertThat(file.name).endsWith("-device-a.json.gz")
        assertThat(settings.settings.first().lastSuccessUtcMs).isEqualTo(clock.millis())

        val second = newDb()
        val backups = manager(second).listBackups().getOrThrow()
        val restored = manager(second).restore(backups.single(), passphrase = null)
        assertThat(restored).isInstanceOf(RestoreOutcome.Success::class.java)
        second.openHelper.readableDatabase.query("SELECT odometerKm, paidPaise FROM refuel WHERE id = 'r1'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getDouble(0)).isEqualTo(42318.5)
            assertThat(c.getLong(1)).isEqualTo(358_024L)
        }
        // Unencrypted backups leave the documents vault on the first phone.
        second.openHelper.readableDatabase.query("SELECT COUNT(*) FROM document").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
        assertThat(settings.imported.single()["homeCityId"]).isEqualTo("bengaluru")
        second.close()
    }

    @Test
    fun encryptedBackupCarriesDocumentsAndNeedsThePassphraseElsewhere() = runTest {
        val docs = FakeDocumentRepository()
        val backups = manager(documents = docs)
        backups.enableEncryption("correct horse battery".toCharArray())
        docs.addAttachment("doc1", Attachment.OwnerType.Document, byteArrayOf(1, 2, 3), "image/jpeg")
        assertThat(backups.backupNow()).isInstanceOf(BackupOutcome.Success::class.java)
        assertThat(drive.files.values.map { it.name }).hasSize(2)
        assertThat(drive.files.values.any { it.name.startsWith("att-") }).isTrue()
        val snapshot = drive.files.values.first { it.name.startsWith("snapshot-") }
        assertThat(snapshot.name).endsWith(".enc")

        // A new phone without the key: the passphrase is asked for, a wrong one is refused, the right one restores.
        keys.clear()
        val second = newDb()
        val remote = manager(second).listBackups().getOrThrow().single()
        assertThat(manager(second).restore(remote, null)).isEqualTo(RestoreOutcome.NeedsPassphrase)
        assertThat(manager(second).restore(remote, "nope".toCharArray())).isEqualTo(RestoreOutcome.WrongPassphrase)
        assertThat(
            manager(second).restore(remote, "correct horse battery".toCharArray()),
        ).isInstanceOf(RestoreOutcome.Success::class.java)
        second.openHelper.readableDatabase.query("SELECT number FROM document WHERE id = 'doc1'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("OG-27-1")
        }
        assertThat(keys.load()).isNotNull()
        second.close()
    }

    @Test
    fun aRestoreThatFailsLeavesTheDataAlone() = runTest {
        // A snapshot whose refuel row breaks a NOT NULL column: the whole restore must roll back.
        val broken = SnapshotCodec.encode(
            Snapshot(
                SnapshotCodec.FORMAT,
                clock.millis(),
                "device-b",
                app.fuelwise.core.database.DatabaseDump(3, mapOf("refuel" to listOf(mapOf("id" to "bad")))),
                emptyMap(),
            ),
        )
        assertThat(manager().importFile(broken, null)).isEqualTo(RestoreOutcome.Failed(BackupError.Unknown))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM refuel").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    @Test
    fun driveErrorsMapToFixes() = runTest {
        drive.failWith = 401
        assertThat(manager().backupNow()).isEqualTo(BackupOutcome.Failed(BackupError.SignIn))
        drive.failWith = 403
        assertThat(manager().backupNow()).isEqualTo(BackupOutcome.Failed(BackupError.StorageFull))
        assertThat(settings.settings.first().lastError).isEqualTo(BackupError.StorageFull)
    }

    @Test
    fun backupFileExportsAndImports() = runTest {
        val bytes = manager().exportFile()
        val second = newDb()
        assertThat(manager(second).importFile(bytes, null)).isInstanceOf(RestoreOutcome.Success::class.java)
        second.openHelper.readableDatabase.query("SELECT COUNT(*) FROM refuel").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
        second.close()
    }

    private class MemoryKeyStore : BackupKeyStore {
        private var key: BackupKey? = null

        override suspend fun load() = key

        override suspend fun save(key: BackupKey) {
            this.key = key
        }

        override suspend fun clear() {
            key = null
        }
    }

    /** Enough of Drive v3 for these tests: list, multipart upload, download, delete, and injected failures. */
    private class FakeDrive : Dispatcher() {
        data class File(val id: String, val name: String, val bytes: ByteArray, val created: String)

        val files = linkedMapOf<String, File>()
        var failWith: Int? = null
        private var next = 1

        override fun dispatch(request: RecordedRequest): MockResponse {
            failWith?.let { code ->
                val body = if (code == 403) """{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""" else "{}"
                return MockResponse.Builder().code(code).body(body).build()
            }
            val path = request.url.encodedPath
            return when {
                request.method == "GET" && path == "/drive/v3/files" -> {
                    val list = files.values.joinToString(",") {
                        val size = it.bytes.size
                        """{"id":"${it.id}","name":"${it.name}","size":"$size","createdTime":"${it.created}"}"""
                    }
                    MockResponse.Builder().body("""{"files":[$list]}""").build()
                }

                request.method == "POST" && path == "/upload/drive/v3/files" -> {
                    val body = request.body?.utf8().orEmpty()
                    val name = Regex("\"name\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    val id = "f${next++}"
                    files[id] = File(id, name, mediaPart(request), "2026-10-16T04:30:00Z")
                    MockResponse.Builder().body("""{"id":"$id"}""").build()
                }

                request.method == "GET" && path.startsWith("/drive/v3/files/") ->
                    MockResponse.Builder().body(
                        Buffer().write(files.getValue(path.substringAfterLast('/')).bytes),
                    ).build()

                request.method == "DELETE" -> {
                    files.remove(path.substringAfterLast('/'))
                    MockResponse.Builder().code(204).build()
                }

                else -> MockResponse.Builder().code(404).build()
            }
        }

        /** The second part of the multipart/related body: the file bytes. */
        private fun mediaPart(request: RecordedRequest): ByteArray {
            val raw = request.body!!.toByteArray()
            val boundary = "--" + request.headers["Content-Type"]!!.substringAfter("boundary=")
            val text = String(raw, Charsets.ISO_8859_1)
            val parts = text.split(boundary)
            val media = parts[2]
            val start = media.indexOf("\r\n\r\n") + 4
            return media.substring(start, media.length - 2).toByteArray(Charsets.ISO_8859_1)
        }
    }
}

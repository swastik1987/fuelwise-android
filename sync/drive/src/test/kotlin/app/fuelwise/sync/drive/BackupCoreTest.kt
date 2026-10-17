package app.fuelwise.sync.drive

import app.fuelwise.core.database.DatabaseDump
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test

/** Codec, crypto and retention: pure JVM. */
class BackupCoreTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private val dump = DatabaseDump(
        schemaVersion = 3,
        tables = mapOf(
            "refuel" to listOf(
                mapOf("id" to "r1", "odometerKm" to 42_318.0, "paidPaise" to 355_800L, "notes" to null, "isFull" to 1L),
            ),
            "vehicle" to listOf(mapOf("id" to "v1", "name" to "Safari", "startOdometerKm" to 42_000.0)),
        ),
    )

    @Test
    fun snapshotRoundTripKeepsSqliteTypes() {
        val snapshot =
            Snapshot(
                SnapshotCodec.FORMAT,
                1_760_000_000_000,
                "device-a",
                dump,
                mapOf("homeCityId" to "bengaluru", "hideAmounts" to true),
            )
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))
        val row = decoded.dump.tables.getValue("refuel").single()
        assertThat(row["odometerKm"]).isEqualTo(42_318.0)
        assertThat(row["paidPaise"]).isEqualTo(355_800L)
        assertThat(row["notes"]).isNull()
        assertThat(decoded.dump.schemaVersion).isEqualTo(3)
        assertThat(decoded.preferences["hideAmounts"]).isEqualTo(true)
        assertThat(decoded.deviceId).isEqualTo("device-a")
    }

    @Test
    fun foreignFilesAreRejected() {
        val notOurs = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.GZIPOutputStream(out).use { it.write("""{"app":"other","format":1}""".toByteArray()) }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { SnapshotCodec.decode(notOurs) }
    }

    @Test
    fun encryptedBackupOpensOnlyWithThePassphrase() {
        val key = BackupCrypto.deriveKey("correct horse battery".toCharArray(), BackupCrypto.newParams())
        val sealed = BackupCrypto.encrypt(key, "hello".toByteArray())
        assertThat(BackupCrypto.isEncrypted(sealed)).isTrue()

        // Another device: derive from the typed passphrase and the parameters in the file header.
        val again = BackupCrypto.deriveKey("correct horse battery".toCharArray(), BackupCrypto.paramsOf(sealed))
        assertThat(BackupCrypto.decrypt(again, sealed).decodeToString()).isEqualTo("hello")

        val wrong = BackupCrypto.deriveKey("wrong horse".toCharArray(), BackupCrypto.paramsOf(sealed))
        assertThrows(WrongPassphraseException::class.java) { BackupCrypto.decrypt(wrong, sealed) }
    }

    @Test
    fun tamperingIsDetected() {
        val key = BackupCrypto.deriveKey("passphrase!".toCharArray(), BackupCrypto.newParams())
        val sealed = BackupCrypto.encrypt(key, ByteArray(64) { it.toByte() })
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertThrows(WrongPassphraseException::class.java) { BackupCrypto.decrypt(key, sealed) }
    }

    @Test
    fun attachmentNamesDontRevealTheContentHash() {
        val key = BackupCrypto.deriveKey("passphrase!".toCharArray(), BackupCrypto.newParams())
        val sha = "a".repeat(64)
        assertThat(key.attachmentName(sha)).doesNotContain(sha)
        assertThat(key.attachmentName(sha)).isEqualTo(key.attachmentName(sha))
    }

    @Test
    fun retentionKeepsTenLatestAndOnePerMonthForAYear() {
        val now = day(2026, 10, 16)
        // 20 recent daily snapshots, plus one at the start of each month back to Oct 2025, plus a very old one.
        val daily = (0 until 20).map { RemoteBackup("d$it", "snapshot-$it", now - it * DAY_MS, 1) }
        val monthly = (1..12).map { m ->
            val at = LocalDate.of(2026, 10, 1).minusMonths(m.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            RemoteBackup("m$m", "snapshot-m$m", at, 1)
        }
        val ancient = RemoteBackup("old", "snapshot-old", day(2024, 1, 1), 1)
        val attachment = RemoteBackup("a", "att-123.bin", day(2020, 1, 1), 1)
        val deleted = snapshotsToDelete(daily + monthly + ancient + attachment, now, zone).map { it.id }.toSet()

        assertThat(deleted).containsNoneIn((0 until 10).map { "d$it" })
        assertThat(deleted).contains("old")
        assertThat(deleted).doesNotContain("a")
        // Oct 2025 is 13 months back: outside the window.
        assertThat(deleted).contains("m12")
        // September's newest is the daily d16 (30 Sep), so the 1 Sep one goes; August keeps its only snapshot.
        assertThat(deleted).doesNotContain("d16")
        assertThat(deleted).contains("m1")
        assertThat(deleted).doesNotContain("m2")
        // Within October the newest snapshot is kept; older October days beyond the latest 10 go.
        assertThat(deleted).contains("d15")
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

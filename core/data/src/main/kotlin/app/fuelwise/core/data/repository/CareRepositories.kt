package app.fuelwise.core.data.repository

import app.fuelwise.core.data.DeviceIdProvider
import app.fuelwise.core.data.mapper.toEntity
import app.fuelwise.core.data.mapper.toModel
import app.fuelwise.core.data.vault.AttachmentStore
import app.fuelwise.core.database.dao.DocumentDao
import app.fuelwise.core.database.dao.FastagDao
import app.fuelwise.core.database.dao.RecurrenceDao
import app.fuelwise.core.database.dao.ReminderDao
import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.domain.recurrence.dueDates
import app.fuelwise.core.domain.reminder.defaultsFor
import app.fuelwise.core.domain.reminder.nextAfterCompletion
import app.fuelwise.core.domain.reminder.snoozed
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.FastagRecharge
import app.fuelwise.core.model.Recurrence
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderCompletion
import app.fuelwise.core.model.ReminderKind
import java.security.MessageDigest
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ReminderRepository {
    fun observeReminders(): Flow<List<Reminder>>

    fun observeCompletions(): Flow<List<ReminderCompletion>>

    suspend fun reminder(id: String): Reminder?

    /** Blank id = new. Returns the id. */
    suspend fun save(reminder: Reminder): String

    suspend fun delete(id: String)

    /** Logs a completion and moves a recurring reminder forward (PRD F-11.4 "Done"). */
    suspend fun complete(id: String, odometerKm: Double?, expenseId: String? = null)

    suspend fun snooze(id: String, days: Long)
}

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>

    fun observeDocument(id: String): Flow<Document?>

    fun observeAttachments(ownerId: String): Flow<List<Attachment>>

    /** Saves and keeps the expiry reminder in step (PRD F-11.5). Blank id = new. */
    suspend fun save(document: Document): String

    /** Tombstones the document, its reminder and attachments; the encrypted files are removed. */
    suspend fun delete(id: String)

    suspend fun addAttachment(ownerId: String, ownerType: Attachment.OwnerType, bytes: ByteArray, mimeType: String): String

    suspend fun removeAttachment(attachment: Attachment)

    suspend fun readAttachment(attachment: Attachment): ByteArray
}

interface RecurrenceRepository {
    fun observeRecurrences(): Flow<List<Recurrence>>

    /** Auto-created expenses waiting for a one-tap confirm (PRD F-04.3). */
    fun observeUnconfirmed(): Flow<List<Expense>>

    suspend fun save(recurrence: Recurrence): String

    suspend fun delete(id: String)

    /** Creates every due entry up to now that doesn't exist yet. Idempotent. Returns how many were created. */
    suspend fun createDueEntries(): Int

    suspend fun confirm(expenseId: String)
}

interface FastagRepository {
    fun observeAccounts(): Flow<List<FastagAccount>>

    fun observeRecharges(): Flow<List<FastagRecharge>>

    suspend fun saveAccount(account: FastagAccount): String

    suspend fun saveRecharge(recharge: FastagRecharge): String

    suspend fun deleteRecharge(id: String)
}

/** Shared write stamping for the M6 repositories (CLAUDE.md rule 3). */
internal class SyncStamper @Inject constructor(private val clock: Clock, private val deviceIds: DeviceIdProvider) {
    suspend fun stamp(createdAt: Long? = null): SyncMeta {
        val now = clock.millis()
        return SyncMeta(createdAt = createdAt ?: now, updatedAt = now, deviceId = deviceIds.deviceId())
    }

    fun now(): Long = clock.millis()

    suspend fun device(): String = deviceIds.deviceId()
}

internal class OfflineReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
    private val zoneId: ZoneId,
) : ReminderRepository {
    override fun observeReminders(): Flow<List<Reminder>> = dao.observeReminders().map { rows -> rows.map { it.toModel() } }

    override fun observeCompletions(): Flow<List<ReminderCompletion>> =
        dao.observeCompletions().map { rows -> rows.map { it.toModel() } }

    override suspend fun reminder(id: String): Reminder? = dao.reminder(id)?.toModel()

    override suspend fun save(reminder: Reminder): String {
        val id = reminder.id.ifBlank { ids.newId() }
        val created = if (reminder.id.isBlank()) null else dao.reminder(reminder.id)?.sync?.createdAt
        dao.upsertReminder(reminder.copy(id = id).toEntity(sync.stamp(created)))
        return id
    }

    override suspend fun delete(id: String) = dao.tombstoneReminder(id, sync.now(), sync.device())

    override suspend fun complete(id: String, odometerKm: Double?, expenseId: String?) {
        val row = dao.reminder(id) ?: return
        val reminder = row.toModel()
        val at = sync.now()
        val next = reminder.nextAfterCompletion(at, odometerKm, zoneId)
        dao.complete(
            completion = ReminderCompletion(ids.newId(), id, reminder.vehicleId, at, odometerKm, expenseId).toEntity(sync.stamp()),
            next = next?.toEntity(sync.stamp(row.sync.createdAt)),
            retireId = if (next == null) id else null,
            at = at,
            deviceId = sync.device(),
        )
    }

    override suspend fun snooze(id: String, days: Long) {
        val row = dao.reminder(id) ?: return
        dao.upsertReminder(row.toModel().snoozed(sync.now(), days, zoneId).toEntity(sync.stamp(row.sync.createdAt)))
    }
}

internal class OfflineDocumentRepository @Inject constructor(
    private val dao: DocumentDao,
    private val reminderDao: ReminderDao,
    private val reminders: ReminderRepository,
    private val vehicles: VehicleRepository,
    private val rules: RulesRepository,
    private val store: AttachmentStore,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<Document>> = dao.observeDocuments().map { rows -> rows.map { it.toModel() } }

    override fun observeDocument(id: String): Flow<Document?> = dao.observeDocument(id).map { it?.toModel() }

    override fun observeAttachments(ownerId: String): Flow<List<Attachment>> =
        dao.observeAttachments(ownerId).map { rows -> rows.map { it.toModel() } }

    override suspend fun save(document: Document): String {
        val id = document.id.ifBlank { ids.newId() }
        val created = if (document.id.isBlank()) null else dao.observeDocument(document.id).first()?.let { it.sync.createdAt }
        val saved = document.copy(id = id)
        dao.upsertDocument(saved.toEntity(sync.stamp(created)))
        syncReminder(saved)
        return id
    }

    /** One reminder per document, following its expiry; removed when the toggle or the date goes. */
    private suspend fun syncReminder(document: Document) {
        val existing = reminderDao.reminderForDocument(document.id)?.toModel()
        val expiry = document.expiryDateUtcMs
        val vehicleId = document.vehicleId
        if (!document.remindOnExpiry || expiry == null || vehicleId == null) {
            existing?.let { reminders.delete(it.id) }
            return
        }
        val kind = document.type.reminderKind()
        val notify = existing?.notifyDaysBefore ?: vehicles.observeVehicle(vehicleId).first()
            ?.let { rules.reminderRules().defaultsFor(kind, it).notifyDaysBefore }
            ?: Reminder.DEFAULT_NOTIFY_DAYS
        reminders.save(
            (existing ?: Reminder("", vehicleId, kind, null, null, null, documentId = document.id))
                .copy(vehicleId = vehicleId, kind = kind, dueDateUtcMs = expiry, notifyDaysBefore = notify, snoozedUntilUtcMs = null),
        )
    }

    override suspend fun delete(id: String) {
        val at = sync.now()
        val device = sync.device()
        dao.tombstoneDocument(id, at, device)
        reminderDao.reminderForDocument(id)?.let { reminderDao.tombstoneReminder(it.id, at, device) }
        dao.observeAttachments(id).first().forEach { removeAttachment(it.toModel()) }
    }

    override suspend fun addAttachment(ownerId: String, ownerType: Attachment.OwnerType, bytes: ByteArray, mimeType: String): String {
        val id = ids.newId()
        val stored = store.write(id, bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        dao.upsertAttachment(Attachment(id, ownerId, ownerType, stored, mimeType, bytes.size.toLong(), digest).toEntity(sync.stamp()))
        return id
    }

    override suspend fun removeAttachment(attachment: Attachment) {
        dao.tombstoneAttachment(attachment.id, sync.now(), sync.device())
        store.delete(attachment.storedName)
    }

    override suspend fun readAttachment(attachment: Attachment): ByteArray = store.read(attachment.id, attachment.storedName)
}

/** The reminder preset a document's expiry maps to. */
fun DocumentType.reminderKind(): ReminderKind = when (this) {
    DocumentType.Insurance -> ReminderKind.Insurance
    DocumentType.Puc -> ReminderKind.Puc
    DocumentType.Rc -> ReminderKind.Rc
    DocumentType.Licence -> ReminderKind.Licence
    DocumentType.Warranty -> ReminderKind.Warranty
    else -> ReminderKind.Custom
}

internal class OfflineRecurrenceRepository @Inject constructor(
    private val dao: RecurrenceDao,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
) : RecurrenceRepository {
    override fun observeRecurrences(): Flow<List<Recurrence>> = dao.observeRecurrences().map { rows -> rows.map { it.toModel() } }

    override fun observeUnconfirmed(): Flow<List<Expense>> = dao.observeUnconfirmed().map { rows -> rows.map { it.toModel() } }

    override suspend fun save(recurrence: Recurrence): String {
        val id = recurrence.id.ifBlank { ids.newId() }
        val created = if (recurrence.id.isBlank()) null else dao.recurrences().firstOrNull { it.id == recurrence.id }?.sync?.createdAt
        dao.upsertRecurrence(recurrence.copy(id = id).toEntity(sync.stamp(created)))
        return id
    }

    override suspend fun delete(id: String) = dao.tombstoneRecurrence(id, sync.now(), sync.device())

    override suspend fun createDueEntries(): Int {
        val now = sync.now()
        var created = 0
        dao.recurrences().map { it.toModel() }.forEach { r ->
            val existing = dao.createdDueDates(r.id).toSet()
            r.dueDates(now).filterNot { it in existing }.forEach { due ->
                val expense = Expense(
                    id = ids.newId(),
                    vehicleId = r.vehicleId,
                    categoryId = r.categoryId,
                    dateTimeUtcMs = due,
                    zoneId = r.zoneId,
                    amount = r.amount,
                    paymentMethod = r.paymentMethod,
                    notes = r.notes,
                    isAutoCreated = true,
                    recurrenceId = r.id,
                )
                if (dao.insertAutoExpense(expense.toEntity(sync.stamp())) != -1L) created++
            }
        }
        return created
    }

    override suspend fun confirm(expenseId: String) = dao.confirm(expenseId, sync.now(), sync.device())
}

internal class OfflineFastagRepository @Inject constructor(
    private val dao: FastagDao,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
) : FastagRepository {
    override fun observeAccounts(): Flow<List<FastagAccount>> = dao.observeAccounts().map { rows -> rows.map { it.toModel() } }

    override fun observeRecharges(): Flow<List<FastagRecharge>> = dao.observeRecharges().map { rows -> rows.map { it.toModel() } }

    override suspend fun saveAccount(account: FastagAccount): String {
        val id = account.id.ifBlank { ids.newId() }
        val created = if (account.id.isBlank()) null else dao.observeAccounts().first().firstOrNull { it.id == account.id }?.sync?.createdAt
        dao.upsertAccount(account.copy(id = id).toEntity(sync.stamp(created)))
        return id
    }

    override suspend fun saveRecharge(recharge: FastagRecharge): String {
        val id = recharge.id.ifBlank { ids.newId() }
        val created = if (recharge.id.isBlank()) null else dao.observeRecharges().first().firstOrNull { it.id == recharge.id }?.sync?.createdAt
        dao.upsertRecharge(recharge.copy(id = id).toEntity(sync.stamp(created)))
        return id
    }

    override suspend fun deleteRecharge(id: String) = dao.tombstoneRecharge(id, sync.now(), sync.device())
}

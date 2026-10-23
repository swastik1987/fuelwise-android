package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.BackupSettingsRepository
import app.fuelwise.core.data.repository.DocumentRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FastagRepository
import app.fuelwise.core.data.repository.ObserveReminderStatuses
import app.fuelwise.core.data.repository.RecurrenceRepository
import app.fuelwise.core.data.repository.ReminderRepository
import app.fuelwise.core.data.repository.RulesRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.reminder.EvaluateReminders
import app.fuelwise.core.domain.reminder.PresetRule
import app.fuelwise.core.domain.reminder.ReminderRules
import app.fuelwise.core.domain.reminder.nextAfterCompletion
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.BackupSettings
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.FastagRecharge
import app.fuelwise.core.model.Recurrence
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderCompletion
import app.fuelwise.core.model.ReminderKind
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory reminders; "now" for completions is [nowUtcMs]. */
class FakeReminderRepository(initial: List<Reminder> = emptyList(), private val nowUtcMs: Long = 0L) :
    ReminderRepository {
    private val state = MutableStateFlow(initial)
    private val completions = MutableStateFlow<List<ReminderCompletion>>(emptyList())
    private var nextId = 1

    val reminders: List<Reminder> get() = state.value

    override fun observeReminders(): Flow<List<Reminder>> = state

    override fun observeCompletions(): Flow<List<ReminderCompletion>> = completions

    override suspend fun reminder(id: String): Reminder? = state.value.firstOrNull { it.id == id }

    override suspend fun save(reminder: Reminder): String {
        val id = reminder.id.ifBlank { "reminder-${nextId++}" }
        state.update { list -> list.filterNot { it.id == id } + reminder.copy(id = id) }
        return id
    }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun complete(id: String, odometerKm: Double?, expenseId: String?): String? {
        val r = reminder(id) ?: return null
        val completionId = "c-${nextId++}"
        completions.update { it + ReminderCompletion(completionId, id, r.vehicleId, nowUtcMs, odometerKm, expenseId) }
        val next = r.nextAfterCompletion(nowUtcMs, odometerKm, ZoneId.of("Asia/Kolkata"))
        if (next == null) delete(id) else save(next)
        return completionId
    }

    override suspend fun completion(id: String): ReminderCompletion? = completions.value.firstOrNull { it.id == id }

    override suspend fun attachExpense(completionId: String, expenseId: String) = completions.update { list ->
        list.map { if (it.id == completionId) it.copy(expenseId = expenseId) else it }
    }

    override suspend fun snooze(id: String, days: Long) {
        reminder(id)?.let { save(it.copy(snoozedUntilUtcMs = nowUtcMs + days * DAY_MS)) }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

class FakeRecurrenceRepository : RecurrenceRepository {
    private val recurrences = MutableStateFlow<List<Recurrence>>(emptyList())
    private val unconfirmed = MutableStateFlow<List<Expense>>(emptyList())

    val saved: List<Recurrence> get() = recurrences.value

    fun addUnconfirmed(vararg expenses: Expense) = unconfirmed.update { it + expenses }

    override fun observeRecurrences(): Flow<List<Recurrence>> = recurrences

    override fun observeUnconfirmed(): Flow<List<Expense>> = unconfirmed

    override suspend fun save(recurrence: Recurrence): String {
        val id = recurrence.id.ifBlank { "recurrence-${recurrences.value.size + 1}" }
        recurrences.update { list -> list.filterNot { it.id == id } + recurrence.copy(id = id) }
        return id
    }

    override suspend fun delete(id: String) = recurrences.update { list -> list.filterNot { it.id == id } }

    override suspend fun createDueEntries(): Int = 0

    override suspend fun confirm(expenseId: String) = unconfirmed.update { list ->
        list.filterNot { it.id == expenseId }
    }
}

class FakeDocumentRepository(initial: List<Document> = emptyList()) : DocumentRepository {
    private val state = MutableStateFlow(initial)
    private val attachments = MutableStateFlow<List<Attachment>>(emptyList())
    private val files = mutableMapOf<String, ByteArray>()
    private var nextAttachment = 1

    val documents: List<Document> get() = state.value

    override fun observeDocuments(): Flow<List<Document>> = state

    override fun observeDocument(id: String): Flow<Document?> = state.map { list -> list.firstOrNull { it.id == id } }

    override fun observeAttachments(ownerId: String): Flow<List<Attachment>> = attachments.map { list ->
        list.filter {
            it.ownerId ==
                ownerId
        }
    }

    override suspend fun save(document: Document): String {
        val id = document.id.ifBlank { "document-${state.value.size + 1}" }
        state.update { list -> list.filterNot { it.id == id } + document.copy(id = id) }
        return id
    }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun addAttachment(
        ownerId: String,
        ownerType: Attachment.OwnerType,
        bytes: ByteArray,
        mimeType: String,
    ): String {
        val id = "attachment-${nextAttachment++}"
        files[id] = bytes
        attachments.update { it + Attachment(id, ownerId, ownerType, "$id.bin", mimeType, bytes.size.toLong(), "") }
        return id
    }

    override suspend fun removeAttachment(attachment: Attachment) = attachments.update { list ->
        list.filterNot {
            it.id ==
                attachment.id
        }
    }

    override suspend fun readAttachment(attachment: Attachment): ByteArray = files[attachment.id] ?: ByteArray(0)

    override suspend fun allAttachments(): List<Attachment> = attachments.value

    override suspend fun restoreAttachmentFile(attachment: Attachment, bytes: ByteArray) = Unit
}

class FakeFastagRepository : FastagRepository {
    private val accounts = MutableStateFlow<List<FastagAccount>>(emptyList())
    private val recharges = MutableStateFlow<List<FastagRecharge>>(emptyList())

    override fun observeAccounts(): Flow<List<FastagAccount>> = accounts

    override fun observeRecharges(): Flow<List<FastagRecharge>> = recharges

    override suspend fun saveAccount(account: FastagAccount): String {
        val id = account.id.ifBlank { "account-${accounts.value.size + 1}" }
        accounts.update { list -> list.filterNot { it.id == id } + account.copy(id = id) }
        return id
    }

    override suspend fun saveRecharge(recharge: FastagRecharge): String {
        val id = recharge.id.ifBlank { "recharge-${recharges.value.size + 1}" }
        recharges.update { list -> list.filterNot { it.id == id } + recharge.copy(id = id) }
        return id
    }

    override suspend fun deleteRecharge(id: String) = recharges.update { list -> list.filterNot { it.id == id } }
}

/** The shipped defaults, in code for tests only (the app reads assets/rules/india.json). */
class FakeRulesRepository : RulesRepository {
    override suspend fun reminderRules() = ReminderRules(
        version = 1,
        presets = mapOf(
            ReminderKind.Insurance to PresetRule(notifyDaysBefore = listOf(30, 15, 7, 1)),
            ReminderKind.Service to PresetRule(12, 10_000.0, listOf(15, 3), twoWheeler = PresetRule(6, 3_000.0)),
        ),
        pucMonthsByNorm = mapOf(EmissionNorm.BS6 to 12, EmissionNorm.BS4 to 6),
        pucDefault = PresetRule(months = 6, notifyDaysBefore = listOf(15, 7, 1)),
        odometerWindowKmCar = 300.0,
        odometerWindowKmTwoWheeler = 150.0,
    )
}

/** [ObserveReminderStatuses] over fakes, for view-model tests. */
fun reminderStatuses(
    reminders: ReminderRepository,
    vehicles: VehicleRepository,
    entries: EntryRepository,
    clock: Clock,
    zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    trips: TripRepository = FakeTripRepository(),
) = ObserveReminderStatuses(
    reminders,
    vehicles,
    entries,
    trips,
    FakeRulesRepository(),
    ComputePredictions(clock),
    EvaluateReminders(clock),
    zone,
)

class FakeBackupSettingsRepository(initial: BackupSettings = BackupSettings()) : BackupSettingsRepository {
    private val state = MutableStateFlow(initial)
    val imported = mutableListOf<Map<String, Any?>>()

    override val settings: Flow<BackupSettings> = state

    override suspend fun update(transform: (BackupSettings) -> BackupSettings) = state.update(transform)

    override suspend fun exportPreferences(): Map<String, Any> = mapOf(
        "homeCityId" to "bengaluru",
        "hideAmounts" to false,
    )

    override suspend fun importPreferences(values: Map<String, Any?>) {
        imported += values
    }
}

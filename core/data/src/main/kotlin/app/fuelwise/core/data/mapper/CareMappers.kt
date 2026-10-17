package app.fuelwise.core.data.mapper

import app.fuelwise.core.database.model.AttachmentEntity
import app.fuelwise.core.database.model.DocumentEntity
import app.fuelwise.core.database.model.FastagAccountEntity
import app.fuelwise.core.database.model.FastagRechargeEntity
import app.fuelwise.core.database.model.RecurrenceEntity
import app.fuelwise.core.database.model.ReminderCompletionEntity
import app.fuelwise.core.database.model.ReminderEntity
import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.FastagRecharge
import app.fuelwise.core.model.InsuranceDetails
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.PolicyType
import app.fuelwise.core.model.Recurrence
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderCompletion
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.RepeatUnit

internal fun ReminderEntity.toModel() = Reminder(
    id = id,
    vehicleId = vehicleId,
    kind = enumValueOrNull<ReminderKind>(kind) ?: ReminderKind.Custom,
    title = title,
    dueDateUtcMs = dueDate,
    dueOdometerKm = dueOdometerKm,
    repeatMonths = repeatMonths,
    repeatKm = repeatKm,
    notifyDaysBefore = notifyDaysBefore.split(',').mapNotNull { it.trim().toIntOrNull() },
    snoozedUntilUtcMs = snoozedUntil,
    documentId = documentId,
    notes = notes,
)

internal fun Reminder.toEntity(sync: SyncMeta) = ReminderEntity(
    id = id,
    vehicleId = vehicleId,
    kind = kind.name,
    title = title?.trim()?.ifEmpty { null },
    dueDate = dueDateUtcMs,
    dueOdometerKm = dueOdometerKm,
    repeatMonths = repeatMonths,
    repeatKm = repeatKm,
    notifyDaysBefore = notifyDaysBefore.distinct().sortedDescending().joinToString(","),
    snoozedUntil = snoozedUntilUtcMs,
    documentId = documentId,
    notes = notes?.trim()?.ifEmpty { null },
    sync = sync,
)

internal fun ReminderCompletionEntity.toModel() =
    ReminderCompletion(id, reminderId, vehicleId, completedAt, odometerKm, expenseId)

internal fun ReminderCompletion.toEntity(sync: SyncMeta) =
    ReminderCompletionEntity(id, reminderId, vehicleId, completedAtUtcMs, odometerKm, expenseId, sync)

internal fun DocumentEntity.toModel() = Document(
    id = id,
    vehicleId = vehicleId,
    type = enumValueOrNull<DocumentType>(type) ?: DocumentType.Other,
    number = number,
    issuer = issuer,
    issueDateUtcMs = issueDate,
    expiryDateUtcMs = expiryDate,
    notes = notes,
    insurance = if (listOf(insurer, policyType, idvPaise, ncbPercent, addOns).all { it == null }) {
        null
    } else {
        InsuranceDetails(insurer, policyType?.let { enumValueOrNull<PolicyType>(it) }, idvPaise?.let(::Money), ncbPercent, addOns)
    },
    remindOnExpiry = remindOnExpiry,
)

internal fun Document.toEntity(sync: SyncMeta) = DocumentEntity(
    id = id,
    vehicleId = vehicleId,
    type = type.name,
    number = number?.trim()?.ifEmpty { null },
    issuer = issuer?.trim()?.ifEmpty { null },
    issueDate = issueDateUtcMs,
    expiryDate = expiryDateUtcMs,
    notes = notes?.trim()?.ifEmpty { null },
    insurer = insurance?.insurer?.trim()?.ifEmpty { null },
    policyType = insurance?.policyType?.name,
    idvPaise = insurance?.idv?.paise,
    ncbPercent = insurance?.ncbPercent,
    addOns = insurance?.addOns?.trim()?.ifEmpty { null },
    remindOnExpiry = remindOnExpiry,
    sync = sync,
)

internal fun AttachmentEntity.toModel() = Attachment(
    id = id,
    ownerId = ownerId,
    ownerType = enumValueOrNull<Attachment.OwnerType>(ownerType) ?: Attachment.OwnerType.Document,
    storedName = storedName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
)

internal fun Attachment.toEntity(sync: SyncMeta) =
    AttachmentEntity(id, ownerId, ownerType.name, storedName, mimeType, sizeBytes, sha256, sync)

internal fun RecurrenceEntity.toModel() = Recurrence(
    id = id,
    vehicleId = vehicleId,
    categoryId = categoryId,
    amount = Money(amountPaise),
    every = every,
    unit = enumValueOrNull<RepeatUnit>(unit) ?: RepeatUnit.Months,
    startUtcMs = startDate,
    zoneId = zoneId,
    endUtcMs = endDate,
    paymentMethod = paymentMethod?.let { enumValueOrNull<PaymentMethod>(it) },
    notes = notes,
)

internal fun Recurrence.toEntity(sync: SyncMeta) = RecurrenceEntity(
    id = id,
    vehicleId = vehicleId,
    categoryId = categoryId,
    amountPaise = amount.paise,
    every = every,
    unit = unit.name,
    startDate = startUtcMs,
    zoneId = zoneId,
    endDate = endUtcMs,
    paymentMethod = paymentMethod?.name,
    notes = notes?.trim()?.ifEmpty { null },
    sync = sync,
)

internal fun FastagAccountEntity.toModel() =
    FastagAccount(id, vehicleId, issuer, tagLast4, Money(anchorBalancePaise), anchorAt, Money(lowBalancePaise))

internal fun FastagAccount.toEntity(sync: SyncMeta) = FastagAccountEntity(
    id = id,
    vehicleId = vehicleId,
    issuer = issuer?.trim()?.ifEmpty { null },
    // Only the last 4 digits are ever kept (PRD F-10.1).
    tagLast4 = tagLast4?.filter(Char::isDigit)?.takeLast(TAG_DIGITS)?.ifEmpty { null },
    anchorBalancePaise = anchorBalance.paise,
    anchorAt = anchorAtUtcMs,
    lowBalancePaise = lowBalanceThreshold.paise,
    sync = sync,
)

internal fun FastagRechargeEntity.toModel() =
    FastagRecharge(id, accountId, vehicleId, dateTime, zoneId, Money(amountPaise), notes)

internal fun FastagRecharge.toEntity(sync: SyncMeta) =
    FastagRechargeEntity(id, accountId, vehicleId, dateTimeUtcMs, zoneId, amount.paise, notes?.trim()?.ifEmpty { null }, sync)

private const val TAG_DIGITS = 4

package app.fuelwise.core.data.mapper

import app.fuelwise.core.database.model.ExpenseEntity
import app.fuelwise.core.database.model.RefuelEntity
import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.PriceSource
import app.fuelwise.core.model.Refuel

internal fun RefuelEntity.toModel() = Refuel(
    id = id,
    vehicleId = vehicleId,
    tankId = tankId,
    dateTimeUtcMs = dateTime,
    zoneId = zoneId,
    odometerKm = odometerKm,
    quantity = quantity,
    unitPrice = Money(unitPricePaise),
    total = Money(totalPaise),
    discount = Money(discountPaise),
    isFull = isFull,
    missedPrevious = missedPrevious,
    grade = grade?.let { enumValueOrNull<FuelGrade>(it) },
    stationName = stationName,
    paymentMethod = paymentMethod?.let { enumValueOrNull<PaymentMethod>(it) },
    notes = notes,
    priceSource = enumValueOrNull<PriceSource>(priceSource) ?: PriceSource.Typed,
    socStartPct = socStart,
    socEndPct = socEnd,
    chargerType = chargerType?.let { enumValueOrNull<ChargerType>(it) },
    chargeDurationMin = chargeDurationMin,
    operator = operator,
)

/** Station brand and coordinates arrive with station detection (F-21.1, M9). */
internal fun Refuel.toEntity(sync: SyncMeta) = RefuelEntity(
    id = id,
    vehicleId = vehicleId,
    tankId = tankId,
    dateTime = dateTimeUtcMs,
    zoneId = zoneId,
    odometerKm = odometerKm,
    quantity = quantity,
    unitPricePaise = unitPrice.paise,
    totalPaise = total.paise,
    discountPaise = discount.paise,
    paidPaise = paid.paise,
    isFull = isFull,
    missedPrevious = missedPrevious,
    grade = grade?.name,
    stationName = stationName?.trim()?.ifEmpty { null },
    stationBrand = null,
    lat = null,
    lng = null,
    paymentMethod = paymentMethod?.name,
    priceSource = priceSource.name,
    notes = notes?.trim()?.ifEmpty { null },
    socStart = socStartPct,
    socEnd = socEndPct,
    chargerType = chargerType?.name,
    chargeDurationMin = chargeDurationMin,
    operator = operator?.trim()?.ifEmpty { null },
    sync = sync,
)

internal fun ExpenseEntity.toModel() = Expense(
    id = id,
    vehicleId = vehicleId,
    categoryId = categoryId,
    dateTimeUtcMs = dateTime,
    zoneId = zoneId,
    amount = Money(amountPaise),
    odometerKm = odometerKm,
    paymentMethod = paymentMethod?.let { enumValueOrNull<PaymentMethod>(it) },
    notes = notes,
    tollPlaza = tollPlaza,
    isAutoCreated = isAutoCreated,
    recurrenceId = recurrenceId,
)

internal fun Expense.toEntity(sync: SyncMeta) = ExpenseEntity(
    id = id,
    vehicleId = vehicleId,
    categoryId = categoryId,
    dateTime = dateTimeUtcMs,
    zoneId = zoneId,
    odometerKm = odometerKm,
    amountPaise = amount.paise,
    paymentMethod = paymentMethod?.name,
    notes = notes?.trim()?.ifEmpty { null },
    tripId = null,
    tollPlaza = tollPlaza?.trim()?.ifEmpty { null },
    recurrenceId = recurrenceId,
    isAutoCreated = isAutoCreated,
    sync = sync,
)

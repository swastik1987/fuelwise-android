package app.fuelwise.core.domain.export

import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.FastagRecharge
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.Recurrence
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.Vehicle
import java.time.ZoneId

/**
 * Everything a CSV export can hold, as read from the repositories: live rows only (tombstoned rows never reach the
 * models), archived vehicles included. Document files are never part of it, only their details.
 */
data class ExportData(
    val vehicles: List<Vehicle> = emptyList(),
    val categories: List<Category> = emptyList(),
    val refuels: List<Refuel> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val documents: List<Document> = emptyList(),
    val fastagAccounts: List<FastagAccount> = emptyList(),
    val fastagRecharges: List<FastagRecharge> = emptyList(),
    val recurrences: List<Recurrence> = emptyList(),
)

/**
 * What to export. [vehicleIds] null means every vehicle; the date range [fromUtcMs, toUtcMs) applies to dated
 * history (fill-ups, expenses, trips, FASTag recharges). Vehicles, reminders, documents and recurrences follow the
 * vehicle filter only. Driver documents (no vehicle) are included only when every vehicle is.
 */
data class ExportFilter(val vehicleIds: Set<String>? = null, val fromUtcMs: Long? = null, val toUtcMs: Long? = null) {
    fun hasVehicle(id: String?): Boolean = vehicleIds == null || id in vehicleIds

    fun hasTime(utcMs: Long): Boolean = (fromUtcMs == null || utcMs >= fromUtcMs) && (toUtcMs == null || utcMs < toUtcMs)
}

data class CsvFile(val name: String, val text: String, val rows: Int)

/**
 * The files of one export. Nothing to export when no row besides vehicles, tanks and categories made it in; with a
 * date range, when no dated row (fill-up, expense, trip, recharge) falls inside it.
 */
data class ExportBundle(val files: List<CsvFile>, val dateLimited: Boolean = false) {
    val isEmpty: Boolean
        get() = files.filter { if (dateLimited) it.name in DATED_FILES else it.name !in REFERENCE_FILES }
            .sumOf { it.rows } == 0

    private companion object {
        val REFERENCE_FILES = setOf(CsvExport.VEHICLES, CsvExport.TANKS, CsvExport.CATEGORIES)
        val DATED_FILES = setOf(CsvExport.REFUELS, CsvExport.EXPENSES, CsvExport.TRIPS, CsvExport.FASTAG_RECHARGES)
    }
}

/**
 * CSV export (PRD F-13.1): one file per entity, columns as documented in `docs/csv-schema.md`. Each dated row has
 * its UTC instant in ms plus a readable local date and time in the row's own zone. Fill-up and expense files use
 * header names the importer (F-13.2) recognises, so an export can be imported again.
 */
object CsvExport {
    const val VEHICLES = "vehicles.csv"
    const val TANKS = "tanks.csv"
    const val CATEGORIES = "categories.csv"
    const val REFUELS = "refuels.csv"
    const val EXPENSES = "expenses.csv"
    const val TRIPS = "trips.csv"
    const val REMINDERS = "reminders.csv"
    const val DOCUMENTS = "documents.csv"
    const val FASTAG_ACCOUNTS = "fastag_accounts.csv"
    const val FASTAG_RECHARGES = "fastag_recharges.csv"
    const val RECURRENCES = "recurrences.csv"

    /**
     * [categoryNames] are the names the user sees (system presets translated), by category id. [zone] dates rows
     * that carry no zone of their own.
     */
    fun export(data: ExportData, filter: ExportFilter, categoryNames: Map<String, String>, zone: ZoneId): ExportBundle {
        val vehicles = data.vehicles.filter { filter.hasVehicle(it.id) }.sortedBy { it.sortOrder }
        val names = data.vehicles.associate { it.id to it.name }
        val context = Context(names, categoryNames, zone)
        return ExportBundle(
            listOf(
                file(VEHICLES, VEHICLE_HEADER, vehicles.map { context.vehicle(it) }),
                file(TANKS, TANK_HEADER, vehicles.flatMap { v -> v.orderedTanks().map { context.tank(v, it) } }),
                file(CATEGORIES, CATEGORY_HEADER, data.categories.sortedBy { it.sortOrder }.map { context.category(it) }),
                file(
                    REFUELS,
                    REFUEL_HEADER,
                    data.refuels.filter { filter.hasVehicle(it.vehicleId) && filter.hasTime(it.dateTimeUtcMs) }
                        .sortedBy { it.dateTimeUtcMs }
                        .map { r -> context.refuel(r, data.vehicles.firstOrNull { it.id == r.vehicleId }) },
                ),
                file(
                    EXPENSES,
                    EXPENSE_HEADER,
                    data.expenses.filter { filter.hasVehicle(it.vehicleId) && filter.hasTime(it.dateTimeUtcMs) }
                        .sortedBy { it.dateTimeUtcMs }.map { context.expense(it) },
                ),
                file(
                    TRIPS,
                    TRIP_HEADER,
                    data.trips.filter { filter.hasVehicle(it.vehicleId) && filter.hasTime(it.startUtcMs) }
                        .sortedBy { it.startUtcMs }.map { context.trip(it) },
                ),
                file(
                    REMINDERS,
                    REMINDER_HEADER,
                    data.reminders.filter { filter.hasVehicle(it.vehicleId) }.map { context.reminder(it) },
                ),
                file(
                    DOCUMENTS,
                    DOCUMENT_HEADER,
                    data.documents.filter { d ->
                        if (d.vehicleId == null) filter.vehicleIds == null else filter.hasVehicle(d.vehicleId)
                    }.map { context.document(it) },
                ),
                file(
                    FASTAG_ACCOUNTS,
                    FASTAG_ACCOUNT_HEADER,
                    data.fastagAccounts.filter { filter.hasVehicle(it.vehicleId) }.map { context.account(it) },
                ),
                file(
                    FASTAG_RECHARGES,
                    FASTAG_RECHARGE_HEADER,
                    data.fastagRecharges.filter { filter.hasVehicle(it.vehicleId) && filter.hasTime(it.dateTimeUtcMs) }
                        .sortedBy { it.dateTimeUtcMs }.map { context.recharge(it) },
                ),
                file(
                    RECURRENCES,
                    RECURRENCE_HEADER,
                    data.recurrences.filter { filter.hasVehicle(it.vehicleId) }.map { context.recurrence(it) },
                ),
            ),
            dateLimited = filter.fromUtcMs != null || filter.toUtcMs != null,
        )
    }

    private fun file(name: String, header: List<String>, rows: List<List<String?>>) =
        CsvFile(name, CsvWriter.write(header, rows), rows.size)

    /** Primary first: the order the importer numbers tanks in (1 = primary). */
    private fun Vehicle.orderedTanks() = tanks.sortedByDescending { it.isPrimary }

    // Header names are part of the format (docs/csv-schema.md): add columns at the end, never rename one.
    private val VEHICLE_HEADER = listOf(
        "id", "name", "type", "make", "model", "variant", "year", "registration_number", "emission_norm",
        "purchase_date", "purchase_odometer_km", "purchase_price_inr", "claimed_km_per_unit", "is_hybrid",
        "custom_km_per_unit", "custom_price_per_unit_inr", "custom_range_km", "archived", "start_odometer_km",
        "start_odometer_utc_ms", "start_odometer_date",
    )
    private val TANK_HEADER =
        listOf("id", "vehicle_id", "vehicle", "tank", "fuel", "unit", "capacity", "is_primary")
    private val CATEGORY_HEADER =
        listOf("id", "system_key", "name", "custom_name", "hidden", "include_in_cost_per_km", "sort_order")
    private val REFUEL_HEADER = listOf(
        "id", "vehicle_id", "vehicle", "date", "time", "zone_id", "date_time_utc_ms", "tank", "tank_id", "fuel",
        "odometer_km", "quantity", "unit", "unit_price_inr", "total_inr", "discount_inr", "paid_inr", "full_tank",
        "missed_previous", "grade", "station", "payment", "price_source", "notes", "soc_start_pct", "soc_end_pct",
        "charger_type", "charge_duration_min", "operator",
    )
    private val EXPENSE_HEADER = listOf(
        "id", "vehicle_id", "vehicle", "date", "time", "zone_id", "date_time_utc_ms", "category_id", "category",
        "amount_inr", "odometer_km", "payment", "toll_plaza", "notes", "trip_id", "recurrence_id", "auto_created",
    )
    private val TRIP_HEADER = listOf(
        "id", "vehicle_id", "vehicle", "start_date", "start_time", "end_date", "end_time", "zone_id",
        "start_utc_ms", "end_utc_ms", "distance_km", "start_odometer_km", "end_odometer_km", "purpose",
        "custom_purpose", "from_place", "to_place", "notes", "est_fuel_cost_inr", "source", "status",
        "recorded_seconds", "idle_seconds", "max_speed_kmh",
    )
    private val REMINDER_HEADER = listOf(
        "id", "vehicle_id", "vehicle", "kind", "title", "due_date", "due_utc_ms", "due_odometer_km",
        "repeat_months", "repeat_km", "notify_days_before", "snoozed_until_utc_ms", "document_id", "notes",
    )
    private val DOCUMENT_HEADER = listOf(
        "id", "vehicle_id", "vehicle", "type", "number", "issuer", "issue_date", "issue_utc_ms", "expiry_date",
        "expiry_utc_ms", "insurer", "policy_type", "idv_inr", "ncb_percent", "add_ons", "remind_on_expiry", "notes",
    )
    private val FASTAG_ACCOUNT_HEADER = listOf(
        "id", "vehicle_id", "vehicle", "issuer", "tag_last4", "anchor_balance_inr", "anchor_date", "anchor_utc_ms",
        "low_balance_inr",
    )
    private val FASTAG_RECHARGE_HEADER = listOf(
        "id", "account_id", "vehicle_id", "vehicle", "date", "time", "zone_id", "date_time_utc_ms", "amount_inr",
        "notes",
    )
    private val RECURRENCE_HEADER = listOf(
        "id", "vehicle_id", "vehicle", "category_id", "category", "amount_inr", "every", "unit", "start_date",
        "start_utc_ms", "end_date", "end_utc_ms", "zone_id", "payment", "notes",
    )

    /** Turns models into rows; names are looked up once. */
    private class Context(
        private val vehicleNames: Map<String, String>,
        private val categoryNames: Map<String, String>,
        private val zone: ZoneId,
    ) {
        private fun vehicleName(id: String?) = id?.let { CsvValues.text(vehicleNames[it]) }

        private fun money(paise: Long?) = paise?.let(CsvValues::rupees)

        private fun km(value: Double?) = value?.let { CsvValues.decimal(it, KM_SCALE) }

        private fun dateOf(utcMs: Long?, zoneId: String? = null) =
            utcMs?.let { CsvValues.date(it, CsvValues.zone(zoneId, zone)) }

        private fun timeOf(utcMs: Long?, zoneId: String?) =
            utcMs?.let { CsvValues.time(it, CsvValues.zone(zoneId, zone)) }

        fun vehicle(v: Vehicle) = listOf(
            v.id, CsvValues.text(v.name), v.type.name, CsvValues.text(v.make), CsvValues.text(v.model),
            CsvValues.text(v.variant), v.year?.toString(), CsvValues.text(v.registrationNumber), v.emissionNorm?.name,
            v.purchaseDate?.toString(), km(v.purchaseOdometerKm), money(v.purchasePrice?.paise),
            v.claimedKmPerUnit?.let { CsvValues.decimal(it) }, CsvValues.flag(v.isHybrid),
            v.custom.kmPerUnit?.let { CsvValues.decimal(it) }, money(v.custom.pricePerUnit?.paise),
            km(v.custom.rangeKm), CsvValues.flag(v.isArchived), km(v.startOdometerKm),
            v.startOdometerAtUtcMs.toString(), dateOf(v.startOdometerAtUtcMs),
        )

        fun tank(v: Vehicle, t: Tank) = listOf(
            t.id, v.id, vehicleName(v.id), (v.orderedTanks().indexOf(t) + 1).toString(), t.energyType.name,
            t.energyType.canonicalUnit.symbol(), t.capacity?.let { CsvValues.decimal(it, QUANTITY_SCALE) },
            CsvValues.flag(t.isPrimary),
        )

        fun category(c: Category) = listOf(
            c.id, c.systemKey?.name, CsvValues.text(categoryNames[c.id] ?: c.customName), CsvValues.text(c.customName),
            CsvValues.flag(c.isHidden), CsvValues.flag(c.includeInCostPerKm), c.sortOrder.toString(),
        )

        fun refuel(r: Refuel, vehicle: Vehicle?): List<String?> {
            val tank = vehicle?.tanks?.firstOrNull { it.id == r.tankId }
            val number = tank?.let { vehicle.orderedTanks().indexOf(it) + 1 }
            return listOf(
                r.id, r.vehicleId, vehicleName(r.vehicleId), dateOf(r.dateTimeUtcMs, r.zoneId),
                timeOf(r.dateTimeUtcMs, r.zoneId), r.zoneId, r.dateTimeUtcMs.toString(), number?.toString(), r.tankId,
                tank?.energyType?.name, km(r.odometerKm), CsvValues.decimal(r.quantity, QUANTITY_SCALE),
                tank?.energyType?.canonicalUnit?.symbol(), money(r.unitPrice.paise), money(r.total.paise),
                money(r.discount.paise), money(r.paid.paise), CsvValues.flag(r.isFull),
                CsvValues.flag(r.missedPrevious), r.grade?.name, CsvValues.text(r.stationName), r.paymentMethod?.name,
                r.priceSource.name, CsvValues.text(r.notes), r.socStartPct?.let { CsvValues.decimal(it, 1) },
                r.socEndPct?.let { CsvValues.decimal(it, 1) }, r.chargerType?.name, r.chargeDurationMin?.toString(),
                CsvValues.text(r.operator),
            )
        }

        fun expense(e: Expense) = listOf(
            e.id, e.vehicleId, vehicleName(e.vehicleId), dateOf(e.dateTimeUtcMs, e.zoneId),
            timeOf(e.dateTimeUtcMs, e.zoneId), e.zoneId, e.dateTimeUtcMs.toString(), e.categoryId,
            CsvValues.text(categoryNames[e.categoryId]), money(e.amount.paise), km(e.odometerKm),
            e.paymentMethod?.name, CsvValues.text(e.tollPlaza), CsvValues.text(e.notes), e.tripId, e.recurrenceId,
            CsvValues.flag(e.isAutoCreated),
        )

        fun trip(t: Trip) = listOf(
            t.id, t.vehicleId, vehicleName(t.vehicleId), dateOf(t.startUtcMs, t.zoneId), timeOf(t.startUtcMs, t.zoneId),
            dateOf(t.endUtcMs, t.zoneId), timeOf(t.endUtcMs, t.zoneId), t.zoneId, t.startUtcMs.toString(),
            t.endUtcMs?.toString(), km(t.distanceKm), km(t.startOdometerKm), km(t.endOdometerKm), t.purpose.name,
            CsvValues.text(t.customPurpose), CsvValues.text(t.fromPlace), CsvValues.text(t.toPlace),
            CsvValues.text(t.notes), money(t.estimatedFuelCost?.paise), t.source.name, t.status.name,
            t.recordedSeconds?.toString(), t.idleSeconds?.toString(), t.maxSpeedKmh?.let { CsvValues.decimal(it, 1) },
        )

        fun reminder(r: Reminder) = listOf(
            r.id, r.vehicleId, vehicleName(r.vehicleId), r.kind.name, CsvValues.text(r.title), dateOf(r.dueDateUtcMs),
            r.dueDateUtcMs?.toString(), km(r.dueOdometerKm), r.repeatMonths?.toString(), km(r.repeatKm),
            r.notifyDaysBefore.joinToString(";"), r.snoozedUntilUtcMs?.toString(), r.documentId,
            CsvValues.text(r.notes),
        )

        fun document(d: Document) = listOf(
            d.id, d.vehicleId, vehicleName(d.vehicleId), d.type.name, CsvValues.text(d.number),
            CsvValues.text(d.issuer), dateOf(d.issueDateUtcMs), d.issueDateUtcMs?.toString(), dateOf(d.expiryDateUtcMs),
            d.expiryDateUtcMs?.toString(), CsvValues.text(d.insurance?.insurer), d.insurance?.policyType?.name,
            money(d.insurance?.idv?.paise), d.insurance?.ncbPercent?.toString(), CsvValues.text(d.insurance?.addOns),
            CsvValues.flag(d.remindOnExpiry), CsvValues.text(d.notes),
        )

        fun account(a: FastagAccount) = listOf(
            a.id, a.vehicleId, vehicleName(a.vehicleId), CsvValues.text(a.issuer), CsvValues.text(a.tagLast4),
            money(a.anchorBalance.paise), dateOf(a.anchorAtUtcMs), a.anchorAtUtcMs.toString(),
            money(a.lowBalanceThreshold.paise),
        )

        fun recharge(r: FastagRecharge) = listOf(
            r.id, r.accountId, r.vehicleId, vehicleName(r.vehicleId), dateOf(r.dateTimeUtcMs, r.zoneId),
            timeOf(r.dateTimeUtcMs, r.zoneId), r.zoneId, r.dateTimeUtcMs.toString(), money(r.amount.paise),
            CsvValues.text(r.notes),
        )

        fun recurrence(r: Recurrence) = listOf(
            r.id, r.vehicleId, vehicleName(r.vehicleId), r.categoryId, CsvValues.text(categoryNames[r.categoryId]),
            money(r.amount.paise), r.every.toString(), r.unit.name, dateOf(r.startUtcMs, r.zoneId),
            r.startUtcMs.toString(), dateOf(r.endUtcMs, r.zoneId), r.endUtcMs?.toString(), r.zoneId,
            r.paymentMethod?.name, CsvValues.text(r.notes),
        )
    }

    private const val KM_SCALE = 2
    private const val QUANTITY_SCALE = 3
}

private fun QuantityUnit.symbol(): String = when (this) {
    QuantityUnit.LITRE -> "L"
    QuantityUnit.KILOGRAM -> "kg"
    QuantityUnit.KILOWATT_HOUR -> "kWh"
}

package app.fuelwise.core.domain.export

import app.fuelwise.core.domain.importing.ColumnMapping
import app.fuelwise.core.domain.importing.Csv
import app.fuelwise.core.domain.importing.EntryKind
import app.fuelwise.core.domain.importing.ImportContext
import app.fuelwise.core.domain.importing.ImportPlanner
import app.fuelwise.core.domain.importing.ImportReader
import app.fuelwise.core.domain.importing.ImportVocabulary
import app.fuelwise.core.domain.importing.MappedTable
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.Test

class CsvExportTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 30) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private val nexon = Vehicle(
        id = "v-nexon",
        name = "Nexon",
        type = VehicleType.Car,
        tanks = listOf(Tank("t-nexon", EnergyType.PETROL, 44.0, isPrimary = true)),
        startOdometerKm = 12_000.0,
        startOdometerAtUtcMs = at(2026, 1, 1),
        registrationNumber = "KA 00 ZZ 0001",
    )
    private val ertiga = Vehicle(
        id = "v-ertiga",
        name = "Ertiga",
        type = VehicleType.Car,
        tanks = listOf(
            Tank("t-ertiga-petrol", EnergyType.PETROL, 45.0, isPrimary = false),
            Tank("t-ertiga-cng", EnergyType.CNG, 12.0, isPrimary = true),
        ),
        startOdometerKm = 30_000.0,
        startOdometerAtUtcMs = at(2026, 1, 1),
    )

    private val refuels = listOf(
        Refuel(
            id = "r1", vehicleId = nexon.id, tankId = "t-nexon", dateTimeUtcMs = at(2026, 9, 2), zoneId = zone.id,
            odometerKm = 12_480.0, quantity = 35.2, unitPrice = Money(10_272), total = Money(361_574),
            isFull = true, stationName = "IndianOil, Koramangala", paymentMethod = PaymentMethod.Upi,
            notes = "Said \"full\", wasn't",
        ),
        Refuel(
            id = "r2", vehicleId = nexon.id, tankId = "t-nexon", dateTimeUtcMs = at(2026, 10, 3, 18, 5),
            zoneId = zone.id, odometerKm = 12_910.5, quantity = 20.0, unitPrice = Money(10_272),
            total = Money(205_440), discount = Money(5_000), isFull = false, paymentMethod = PaymentMethod.Card,
        ),
        Refuel(
            id = "r3", vehicleId = ertiga.id, tankId = "t-ertiga-cng", dateTimeUtcMs = at(2026, 10, 5),
            zoneId = zone.id, odometerKm = 30_350.0, quantity = 8.5, unitPrice = Money(8_850),
            total = Money(75_225), isFull = true, paymentMethod = PaymentMethod.Cash,
        ),
        Refuel(
            id = "r4", vehicleId = ertiga.id, tankId = "t-ertiga-petrol", dateTimeUtcMs = at(2026, 10, 7),
            zoneId = zone.id, odometerKm = 30_420.0, quantity = 10.0, unitPrice = Money(10_272),
            total = Money(102_720), isFull = false,
        ),
    )
    private val expenses = listOf(
        Expense(
            id = "e1", vehicleId = nexon.id, categoryId = SystemCategory.Service.id, dateTimeUtcMs = at(2026, 9, 20),
            zoneId = zone.id, amount = Money(458_000), odometerKm = 12_700.0, paymentMethod = PaymentMethod.Card,
            notes = "=SUM(A1) oil, filter",
        ),
        Expense(
            id = "e2",
            vehicleId = nexon.id,
            categoryId = SystemCategory.TollFastag.id,
            dateTimeUtcMs = at(2026, 10, 4),
            zoneId = zone.id,
            amount = Money(16_500),
            paymentMethod = PaymentMethod.FastagWallet,
            tollPlaza = "Attibele",
        ),
    )
    private val categories = SystemCategory.entries.mapIndexed { i, key ->
        Category(key.id, key, null, isHidden = false, includeInCostPerKm = key.includeInCostPerKm, sortOrder = i)
    }
    private val names = mapOf(SystemCategory.Service.id to "Service", SystemCategory.TollFastag.id to "Toll (FASTag)")

    private val data = ExportData(
        vehicles = listOf(nexon, ertiga),
        categories = categories,
        refuels = refuels,
        expenses = expenses,
        trips = listOf(
            Trip(
                "trip1",
                nexon.id,
                at(2026, 10, 6),
                zone.id,
                42.5,
                TripPurpose.Business,
                fromPlace = "HSR Layout",
                toPlace = "Whitefield",
            ),
        ),
        documents = listOf(
            Document("d1", nexon.id, DocumentType.Puc, number = "PUC123", expiryDateUtcMs = at(2027, 3, 1, 0, 0)),
            Document("d2", null, DocumentType.Licence, number = "DL-0000"),
        ),
    )

    private fun export(filter: ExportFilter = ExportFilter()) = CsvExport.export(data, filter, names, zone)

    private fun ExportBundle.file(name: String) = files.first { it.name == name }

    private fun rows(file: CsvFile) = Csv.parse(file.text).rows.map { r -> r.map { it.asText() } }

    @Test
    fun `money is rupees with two decimals from paise`() {
        assertThat(CsvValues.rupees(0)).isEqualTo("0.00")
        assertThat(CsvValues.rupees(5)).isEqualTo("0.05")
        assertThat(CsvValues.rupees(-5)).isEqualTo("-0.05")
        assertThat(CsvValues.rupees(-1_234)).isEqualTo("-12.34")
        assertThat(CsvValues.rupees(12_345_678)).isEqualTo("123456.78")
        assertThat(CsvValues.rupees(Long.MAX_VALUE)).isEqualTo("92233720368547758.07")
    }

    @Test
    fun `fields are quoted only when needed and formulas are defused`() {
        assertThat(CsvWriter.field("Attibele")).isEqualTo("Attibele")
        assertThat(CsvWriter.field("IndianOil, Koramangala")).isEqualTo("\"IndianOil, Koramangala\"")
        assertThat(CsvWriter.field("Said \"full\"")).isEqualTo("\"Said \"\"full\"\"\"")
        assertThat(CsvValues.text("=SUM(A1)")).isEqualTo("'=SUM(A1)")
        assertThat(CsvValues.text("HSR")).isEqualTo("HSR")
        val text = CsvWriter.write(listOf("a", "b"), listOf(listOf("1", null)))
        assertThat(text).isEqualTo(Char(0xFEFF) + "a,b\r\n1,\r\n")
    }

    @Test
    fun `each entity gets a file with readable local dates and canonical units`() {
        val bundle = export()
        assertThat(bundle.files.map { it.name }).containsExactly(
            "vehicles.csv", "tanks.csv", "categories.csv", "refuels.csv", "expenses.csv", "trips.csv",
            "fastag_recharges.csv", "reminders.csv", "documents.csv", "fastag_accounts.csv", "recurrences.csv",
        ).inOrder()
        val refuelRows = rows(bundle.file(CsvExport.REFUELS))
        val header = refuelRows.first()
        val second = refuelRows[2].let { r -> header.indices.associate { header[it] to r.getOrElse(it) { "" } } }
        assertThat(second["date"]).isEqualTo("2026-10-03")
        assertThat(second["time"]).isEqualTo("18:05")
        assertThat(second["date_time_utc_ms"]).isEqualTo(at(2026, 10, 3, 18, 5).toString())
        assertThat(second["odometer_km"]).isEqualTo("12910.5")
        assertThat(second["quantity"]).isEqualTo("20")
        assertThat(second["unit"]).isEqualTo("L")
        assertThat(second["total_inr"]).isEqualTo("2054.40")
        assertThat(second["discount_inr"]).isEqualTo("50.00")
        assertThat(second["paid_inr"]).isEqualTo("2004.40")
        assertThat(second["full_tank"]).isEqualTo("false")
        // Ertiga lists the CNG tank (primary) as tank 1, the importer's numbering.
        val tanks = rows(bundle.file(CsvExport.TANKS)).drop(1).associate { it[0] to it[3] }
        assertThat(tanks).containsEntry("t-ertiga-cng", "1")
        assertThat(tanks).containsEntry("t-ertiga-petrol", "2")
        // A formula in notes stays text.
        assertThat(bundle.file(CsvExport.EXPENSES).text).contains("'=SUM(A1) oil")
    }

    @Test
    fun `filters by vehicle and date range`() {
        val october = ExportFilter(
            vehicleIds = setOf(nexon.id),
            fromUtcMs = at(2026, 10, 1, 0, 0),
            toUtcMs = at(2026, 11, 1, 0, 0),
        )
        val bundle = export(october)
        assertThat(rows(bundle.file(CsvExport.REFUELS)).drop(1).map { it[0] }).containsExactly("r2")
        assertThat(rows(bundle.file(CsvExport.EXPENSES)).drop(1).map { it[0] }).containsExactly("e2")
        assertThat(rows(bundle.file(CsvExport.VEHICLES)).drop(1).map { it[0] }).containsExactly(nexon.id)
        // Driver documents only come with an every-vehicle export.
        assertThat(rows(bundle.file(CsvExport.DOCUMENTS)).drop(1).map { it[0] }).containsExactly("d1")
        assertThat(rows(export().file(CsvExport.DOCUMENTS)).drop(1).map { it[0] }).containsExactly("d1", "d2")
    }

    @Test
    fun `nothing to export when the range holds no entries`() {
        val empty = export(ExportFilter(fromUtcMs = at(2020, 1, 1), toUtcMs = at(2020, 2, 1)))
        assertThat(empty.file(CsvExport.VEHICLES).rows).isEqualTo(2)
        assertThat(empty.isEmpty).isTrue()
        assertThat(export().isEmpty).isFalse()
        val noEntries = ExportData(vehicles = listOf(nexon), categories = categories)
        assertThat(CsvExport.export(noEntries, ExportFilter(), names, zone).isEmpty).isTrue()
    }

    @Test
    fun `zip holds every file`() {
        val bundle = export()
        val bytes = ZipWriter.write(bundle.files, at(2026, 10, 19))
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { names += it.name }
        }
        assertThat(names).isEqualTo(bundle.files.map { it.name })
    }

    // --- Round trip with the F-13.2 importer ----------------------------------------------------------------------

    private val vocabulary = ImportVocabulary(categories = names.entries.associate { it.value to it.key })

    private fun reimport(file: CsvFile, kind: EntryKind, existingRefuels: List<Refuel> = emptyList()) =
        ImportReader.read(file.text.toByteArray(), vocabulary).let { read ->
            val table = requireNotNull(read.unmapped)
            val mapping = ColumnMapping.suggest(table.headers, kind, vocabulary)
            assertThat(ColumnMapping.guessKind(table.headers, vocabulary)).isEqualTo(kind)
            assertThat(mapping.isComplete).isTrue()
            ImportPlanner.plan(
                listOf(MappedTable(table, mapping)),
                ImportContext(listOf(nexon, ertiga), null, existingRefuels, emptyList(), vocabulary, zone),
            )
        }

    @Test
    fun `exported fill-ups import again unchanged`() {
        val plan = reimport(export().file(CsvExport.REFUELS), EntryKind.Fuel)
        assertThat(plan.problems).isEmpty()
        assertThat(plan.refuels).hasSize(refuels.size)
        plan.refuels.zip(refuels).forEach { (back, original) ->
            assertThat(back.vehicleId).isEqualTo(original.vehicleId)
            assertThat(back.tankId).isEqualTo(original.tankId)
            assertThat(back.dateTimeUtcMs).isEqualTo(original.dateTimeUtcMs)
            assertThat(back.odometerKm).isEqualTo(original.odometerKm)
            assertThat(back.quantity).isEqualTo(original.quantity)
            assertThat(back.unitPrice).isEqualTo(original.unitPrice)
            assertThat(back.total).isEqualTo(original.total)
            assertThat(back.isFull).isEqualTo(original.isFull)
            assertThat(back.stationName).isEqualTo(original.stationName)
            assertThat(back.paymentMethod).isEqualTo(original.paymentMethod)
            assertThat(back.notes).isEqualTo(original.notes)
        }
        // Importing the export into the same data adds nothing: every row is a duplicate.
        val again = reimport(export().file(CsvExport.REFUELS), EntryKind.Fuel, existingRefuels = refuels)
        assertThat(again.refuels).isEmpty()
        assertThat(again.duplicates).isEqualTo(refuels.size)
    }

    @Test
    fun `exported expenses import again with their category, amount and plaza`() {
        val plan = reimport(export().file(CsvExport.EXPENSES), EntryKind.Expense)
        assertThat(plan.problems).isEmpty()
        assertThat(plan.otherCategoryRows).isEqualTo(0)
        assertThat(plan.expenses.map { it.categoryId }).isEqualTo(expenses.map { it.categoryId })
        assertThat(plan.expenses.map { it.amount }).isEqualTo(expenses.map { it.amount })
        assertThat(plan.expenses.map { it.odometerKm }).isEqualTo(expenses.map { it.odometerKm })
        assertThat(plan.expenses.map { it.paymentMethod }).isEqualTo(expenses.map { it.paymentMethod })
        assertThat(plan.expenses.map { it.tollPlaza }).isEqualTo(expenses.map { it.tollPlaza })
        assertThat(plan.expenses.map { it.vehicleId }).isEqualTo(expenses.map { it.vehicleId })
    }
}

package app.fuelwise.core.domain.importing

import app.fuelwise.core.domain.importing.xlsx.XlsxReader
import app.fuelwise.core.domain.importing.xlsx.XlsxValue
import app.fuelwise.core.domain.importing.xlsx.XlsxWriter
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.ImportSource
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ImportTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val vocabulary = ImportVocabulary()

    private val safari = Vehicle(
        id = "safari",
        name = "Safari",
        type = VehicleType.Car,
        tanks = listOf(Tank("safari-diesel", EnergyType.DIESEL, 50.0, isPrimary = true)),
        startOdometerKm = 41_000.0,
        startOdometerAtUtcMs = 0,
        registrationNumber = "KA 01 AB 0000",
    )
    private val ertiga = Vehicle(
        id = "ertiga",
        name = "Ertiga",
        type = VehicleType.Car,
        tanks = listOf(
            Tank("ertiga-cng", EnergyType.CNG, 12.0, isPrimary = true),
            Tank("ertiga-petrol", EnergyType.PETROL, 45.0, isPrimary = false),
        ),
        startOdometerKm = 10_000.0,
        startOdometerAtUtcMs = 0,
    )

    private fun context(
        refuels: List<Refuel> = emptyList(),
        expenses: List<Expense> = emptyList(),
        defaultVehicle: String? = "safari",
        order: DateOrder = DateOrder.DayMonthYear,
    ) = ImportContext(listOf(safari, ertiga), defaultVehicle, refuels, expenses, vocabulary, zone, order)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun text(vararg values: String?) = values.map { if (it == null) Cell.Empty else Cell.Text(it) }

    // --- Values ---------------------------------------------------------------------------------------------------

    @Test
    fun `numbers read Indian and European grouping and currency marks`() {
        assertThat(ImportValues.number("₹1,23,456.78")).isEqualTo(BigDecimal("123456.78"))
        assertThat(ImportValues.number("Rs. 450")).isEqualTo(BigDecimal("450"))
        assertThat(ImportValues.number("1.234,56")).isEqualTo(BigDecimal("1234.56"))
        assertThat(ImportValues.number("12,5")).isEqualTo(BigDecimal("12.5"))
        assertThat(ImportValues.number("45,000")).isEqualTo(BigDecimal("45000"))
        assertThat(ImportValues.number("-20")).isNull()
        assertThat(ImportValues.number("abc")).isNull()
        assertThat(ImportValues.money(Cell.Text("3580.245"))).isEqualTo(Money(358_025))
    }

    @Test
    fun `dates read many layouts and Excel serials`() {
        val dmy = DateOrder.DayMonthYear
        val expected = LocalDateTime.of(2026, 10, 16, 0, 0)
        listOf("2026-10-16", "16/10/2026", "16-10-26", "16.10.2026", "16 Oct 2026", "Oct 16, 2026", "16-October-2026")
            .forEach { assertThat(ImportValues.dateTime(Cell.Text(it), dmy)).isEqualTo(expected) }
        assertThat(
            ImportValues.dateTime(Cell.Text("2026-08-24 18:40"), dmy),
        ).isEqualTo(LocalDateTime.of(2026, 8, 24, 18, 40))
        // 46311 is 16 Oct 2026 in Excel; .75 is 18:00.
        assertThat(ImportValues.dateTime(Cell.Number(46_311.75), dmy)).isEqualTo(LocalDateTime.of(2026, 10, 16, 18, 0))
        assertThat(ImportValues.dateTime(Cell.Text("03/04/2026"), DateOrder.MonthDayYear))
            .isEqualTo(LocalDateTime.of(2026, 3, 4, 0, 0))
        assertThat(ImportValues.dateTime(Cell.Text("31/02/2026"), dmy)).isNull()
        assertThat(ImportValues.time(Cell.Text("6:40 pm"))).isEqualTo(LocalTime.of(18, 40))
        assertThat(ImportValues.time(Cell.Number(0.5))).isEqualTo(LocalTime.NOON)
    }

    @Test
    fun `a date column decides the order when a day is above 12`() {
        assertThat(ImportValues.detectDateOrder(text("03/04/2026", "25/04/2026"))).isEqualTo(DateOrder.DayMonthYear)
        assertThat(ImportValues.detectDateOrder(text("03/04/2026", "04/25/2026"))).isEqualTo(DateOrder.MonthDayYear)
        assertThat(ImportValues.detectDateOrder(text("03/04/2026"))).isNull()
    }

    // --- CSV ------------------------------------------------------------------------------------------------------

    @Test
    fun `csv handles quotes, semicolons, line breaks and a BOM`() {
        val sheet = Csv.parse(
            Char(0xFEFF) + "Date;Notes;Amount\r\n16/10/2026;\"Oil; filter\nand \"\"wash\"\"\";1.250,50\r\n\r\n",
        )
        assertThat(sheet.rows).hasSize(2)
        assertThat(sheet.rows[0].map { it.asText() }).containsExactly("Date", "Notes", "Amount").inOrder()
        assertThat(sheet.rows[1][1].asText()).isEqualTo("Oil; filter\nand \"wash\"")
        assertThat(ImportValues.number(sheet.rows[1][2])).isEqualTo(BigDecimal("1250.50"))
    }

    // --- Mapping --------------------------------------------------------------------------------------------------

    @Test
    fun `mapping suggestions match common headers and the kind is guessed`() {
        val headers = listOf("Date", "Odometer (km)", "Litres", "Rate", "Total (₹)", "Full tank", "Pump", "Remarks")
        assertThat(ColumnMapping.guessKind(headers, vocabulary)).isEqualTo(EntryKind.Fuel)
        val mapping = ColumnMapping.suggest(headers, EntryKind.Fuel, vocabulary)
        assertThat(mapping.columns).containsExactly(
            ImportField.Date, 0, ImportField.Odometer, 1, ImportField.Quantity, 2, ImportField.UnitPrice, 3,
            ImportField.Total, 4, ImportField.FullTank, 5, ImportField.Station, 6, ImportField.Notes, 7,
        )
        assertThat(mapping.isComplete).isTrue()
        assertThat(ColumnMapping.decode(mapping.encode())).isEqualTo(mapping)
        assertThat(
            ColumnMapping.guessKind(listOf("Date", "Category", "Amount"), vocabulary),
        ).isEqualTo(EntryKind.Expense)
    }

    @Test
    fun `localized template headers map exactly`() {
        val hindi = ImportVocabulary(
            headers = mapOf(
                ImportField.Odometer to setOf("ओडोमीटर (km)"),
                ImportField.Date to setOf("तारीख"),
            ),
        )
        val mapping = ColumnMapping.suggest(listOf("तारीख", "ओडोमीटर (km)"), EntryKind.Expense, hindi)
        assertThat(mapping.columns).containsExactly(ImportField.Date, 0, ImportField.Odometer, 1)
    }

    // --- Planner --------------------------------------------------------------------------------------------------

    private fun fuelTable(vararg rows: List<Cell>) = MappedTable(
        RawTable(null, ImportTemplate.fuelColumns.map { it.name }, rows.toList(), 2),
        ImportTemplate.positionalMapping(EntryKind.Fuel),
    )

    private fun expenseTable(vararg rows: List<Cell>) = MappedTable(
        RawTable("Expenses", ImportTemplate.expenseColumns.map { it.name }, rows.toList(), 2),
        ImportTemplate.positionalMapping(EntryKind.Expense),
    )

    // Vehicle, Date, Time, Odometer, Fuel, Quantity, Price, Total, Full, Missed, Station, Payment, Notes
    private fun fuelRow(
        vehicle: String? = "Safari",
        date: String? = "16/10/2026",
        odo: String? = "42318",
        fuel: String? = null,
        qty: String? = "40.25",
        price: String? = "88.95",
        total: String? = null,
        full: String? = null,
        payment: String? = "UPI",
    ) = text(vehicle, date, "9:30", odo, fuel, qty, price, total, full, null, "IOCL Indiranagar", payment, null)

    @Test
    fun `fill-ups get the third price figure and the right tank`() {
        val plan = ImportPlanner.plan(
            listOf(
                fuelTable(
                    fuelRow(),
                    fuelRow(
                        vehicle = "Ertiga",
                        odo = "12000",
                        fuel = "Petrol",
                        qty = null,
                        price = "101.50",
                        total = "2030",
                    ),
                    fuelRow(
                        vehicle = "ertiga",
                        odo = "12300",
                        fuel = "CNG",
                        qty = "8.5",
                        price = null,
                        total = "765",
                        full = "No",
                    ),
                ),
            ),
            context(),
        )
        assertThat(plan.problems).isEmpty()
        val (first, second, third) = plan.refuels
        assertThat(first.total).isEqualTo(Money(358_024))
        assertThat(first.tankId).isEqualTo("safari-diesel")
        assertThat(first.dateTimeUtcMs).isEqualTo(at(2026, 10, 16, 9, 30))
        assertThat(first.paymentMethod).isEqualTo(PaymentMethod.Upi)
        assertThat(first.isFull).isTrue()
        assertThat(second.tankId).isEqualTo("ertiga-petrol")
        assertThat(second.quantity).isEqualTo(20.0)
        assertThat(third.tankId).isEqualTo("ertiga-cng")
        assertThat(third.unitPrice).isEqualTo(Money(9_000))
        assertThat(third.isFull).isFalse()
    }

    @Test
    fun `problems name the row and the value`() {
        val plan = ImportPlanner.plan(
            listOf(
                fuelTable(
                    fuelRow(vehicle = "Swift"),
                    fuelRow(date = "31/02/2026"),
                    fuelRow(odo = null),
                    fuelRow(qty = null, price = null, total = "2000"),
                    fuelRow(fuel = "Electric"),
                    fuelRow(qty = "forty"),
                    text(null, null, null, null),
                ),
            ),
            context(),
        )
        assertThat(plan.refuels).isEmpty()
        assertThat(plan.problems.map { it.at.row to it.issue }).containsExactly(
            2 to RowIssue.VehicleNotFound,
            3 to RowIssue.BadDate,
            4 to RowIssue.MissingOdometer,
            5 to RowIssue.MissingFuelAmounts,
            6 to RowIssue.FuelNotOnVehicle,
            7 to RowIssue.BadNumber,
        ).inOrder()
        assertThat(plan.problems.first().value).isEqualTo("Swift")
    }

    @Test
    fun `blank vehicle uses the chosen one, and registration numbers match too`() {
        val plan = ImportPlanner.plan(
            listOf(
                expenseTable(
                    text(null, "16/10/2026", "Service", "4850"),
                    text("ka01ab0000", "17/10/2026", "Wash", "350"),
                ),
            ),
            context(),
        )
        assertThat(plan.expenses.map { it.vehicleId }).containsExactly("safari", "safari")
        val none = ImportPlanner.plan(
            listOf(expenseTable(text(null, "16/10/2026", "Service", "4850"))),
            context(defaultVehicle = null),
        )
        assertThat(none.problems.single().issue).isEqualTo(RowIssue.NoVehicle)
    }

    @Test
    fun `unknown categories go under Other with the name kept in the notes`() {
        val plan = ImportPlanner.plan(
            listOf(
                expenseTable(
                    // Vehicle, Date, Category, Amount, Odometer, Payment, Toll plaza, Notes
                    text("Safari", "16/10/2026", "Toll (FASTag)", "₹ 235", null, "FASTag", "Nelamangala", null),
                    text("Safari", "17/10/2026", "Dashcam", "3,999", "42,600", "Card", null, "Front"),
                    text("Safari", "18/10/2026", null, "100"),
                    text("Safari", "19/10/2026", "Service", null),
                ),
            ),
            context(),
        )
        val (toll, dashcam, blank) = plan.expenses
        assertThat(toll.categoryId).isEqualTo(SystemCategory.TollFastag.id)
        assertThat(toll.tollPlaza).isEqualTo("Nelamangala")
        assertThat(toll.paymentMethod).isEqualTo(PaymentMethod.FastagWallet)
        assertThat(dashcam.categoryId).isEqualTo(SystemCategory.Other.id)
        assertThat(dashcam.notes).isEqualTo("Dashcam · Front")
        assertThat(dashcam.odometerKm).isEqualTo(42_600.0)
        assertThat(blank.categoryId).isEqualTo(SystemCategory.Other.id)
        assertThat(plan.otherCategoryRows).isEqualTo(1)
        assertThat(plan.problems.single()).isEqualTo(RowProblem(RowRef("Expenses", 5), RowIssue.MissingAmount))
    }

    @Test
    fun `duplicates of existing entries and of earlier rows are skipped`() {
        val existing = Refuel(
            "r1", "safari", "safari-diesel", at(2026, 10, 16, 7), zone.id, 42_318.4, 40.25,
            Money(8_895), Money(358_024), isFull = true,
        )
        val expense = Expense("e1", "safari", SystemCategory.Service.id, at(2026, 10, 16), zone.id, Money(485_000))
        val plan = ImportPlanner.plan(
            listOf(
                fuelTable(fuelRow(), fuelRow(odo = "42800"), fuelRow(odo = "42800")),
                expenseTable(text("Safari", "16/10/2026", "Service", "4850")),
            ),
            context(listOf(existing), listOf(expense)),
        )
        assertThat(plan.refuels.map { it.odometerKm }).containsExactly(42_800.0)
        assertThat(plan.expenses).isEmpty()
        assertThat(plan.duplicates).isEqualTo(3)
    }

    @Test
    fun `too many rows stops the plan`() {
        val rows = List(ImportPlanner.MAX_ROWS + 1) { fuelRow() }
        val plan = ImportPlanner.plan(listOf(fuelTable(*rows.toTypedArray())), context())
        assertThat(plan.problems.single().issue).isEqualTo(RowIssue.TooManyRows)
        assertThat(plan.entryCount).isEqualTo(0)
    }

    // --- Fuelio ---------------------------------------------------------------------------------------------------

    private fun fuelioBytes() = requireNotNull(javaClass.getResource("/import/fuelio-sample.csv")).readBytes()

    @Test
    fun `fuelio export imports fill-ups and costs`() {
        val file = ImportReader.read(fuelioBytes(), vocabulary)
        assertThat(file.source).isEqualTo(ImportSource.Fuelio)
        assertThat(file.vehicleHint).isEqualTo("Safari")
        assertThat(file.problems.single().issue).isEqualTo(RowIssue.IncomeNotSupported)

        val plan = ImportPlanner.plan(file.tables, context(defaultVehicle = "safari"), file.problems)
        assertThat(plan.refuels).hasSize(3)
        val second = plan.refuels[1]
        assertThat(second.odometerKm).isEqualTo(42_318.5)
        assertThat(second.quantity).isEqualTo(40.25)
        assertThat(second.total).isEqualTo(Money(358_024))
        assertThat(second.unitPrice).isEqualTo(Money(8_895))
        assertThat(second.stationName).isEqualTo("Bengaluru")
        assertThat(second.notes).isEqualTo("Highway trip")
        assertThat(second.dateTimeUtcMs).isEqualTo(at(2026, 8, 24, 18, 40))
        assertThat(plan.refuels[2].isFull).isFalse()

        // The reminder template row is skipped; the income row is a problem; "Dashcam" goes under Other.
        assertThat(plan.expenses.map { it.categoryId }).containsExactly(
            SystemCategory.Service.id,
            SystemCategory.Wash.id,
            SystemCategory.Other.id,
        ).inOrder()
        assertThat(plan.expenses[0].notes).isEqualTo("3rd service · Oil + filter")
        assertThat(plan.expenses[0].amount).isEqualTo(Money(485_000))
        assertThat(plan.expenses[2].notes).isEqualTo("Dashcam · Front camera")
        assertThat(plan.problems.single().at.row).isEqualTo(20)
    }

    @Test
    fun `fuelio miles and gallons become km and litres`() {
        val text = fuelioBytes().decodeToString().replace("\"Family car\",\"0\",\"0\"", "\"Family car\",\"1\",\"1\"")
        val file = ImportReader.read(text.toByteArray(), vocabulary)
        val plan = ImportPlanner.plan(file.tables, context())
        val first = plan.refuels.first()
        assertThat(first.odometerKm).isWithin(0.01).of(41_850 * 1.609344)
        assertThat(first.quantity).isWithin(0.01).of(42.10 * 3.785411784)
        assertThat(first.total).isEqualTo(Money(374_521))
    }

    // --- Template and xlsx ----------------------------------------------------------------------------------------

    private val labels = ImportTemplate.Labels(
        fuelSheet = "Fuel",
        expenseSheet = "Expenses",
        howToSheet = "How to fill",
        headers = ImportField.entries.associateWith { it.name },
        help = emptyMap(),
        examples = emptyMap(),
        howToTitle = "How to fill",
        howToIntro = listOf("One row per entry."),
        howToColumns = Triple("Column", "What to enter", "Example"),
    )
    private val lists = ImportTemplate.Lists(
        vehicles = listOf("Safari", "Ertiga"),
        energies = listOf("Diesel", "CNG", "Petrol"),
        categories = SystemCategory.entries.map { it.name },
        payments = listOf("UPI", "Card"),
        yesNo = listOf("Yes", "No"),
    )

    @Test
    fun `the template is a valid workbook that comes back recognised`() {
        val bytes = ImportTemplate.write(labels, lists)
        assertThat(XlsxReader.looksLikeXlsx(bytes)).isTrue()
        val sheets = XlsxReader.read(bytes)
        assertThat(
            sheets.map {
                it.name
            },
        ).containsExactly("Fuel", "Expenses", "How to fill", "Lists", "_fuelwise").inOrder()
        assertThat(sheets.filter { it.hidden }.map { it.name }).containsExactly("Lists", "_fuelwise")
        assertThat(ImportTemplate.isTemplate(sheets)).isTrue()
        assertThat(sheets[0].rows[0].map { it.asText() }).isEqualTo(ImportTemplate.fuelColumns.map { it.name })
        assertThat(sheets.first { it.name == "Lists" }.cell(1, 0).asText()).isEqualTo("Ertiga")
    }

    @Test
    fun `a filled template imports both sheets, numbers and serial dates included`() {
        val sheets = ImportTemplate.sheets(labels, lists).toMutableList()
        sheets[0] = sheets[0].copy(
            rows = listOf(
                listOf(
                    XlsxValue.Text(
                        "Safari",
                    ),
                    XlsxValue.Number(46_311.0), XlsxValue.Number(0.75), XlsxValue.Number(42_318.0),
                    XlsxValue.Empty,
                    XlsxValue.Number(
                        40.25,
                    ),
                    XlsxValue.Number(88.95), XlsxValue.Empty, XlsxValue.Text("Yes"),
                ),
            ),
        )
        sheets[1] = sheets[1].copy(
            rows = listOf(
                listOf(
                    XlsxValue.Text("Safari"),
                    XlsxValue.Text("16-10-2026"),
                    XlsxValue.Text("Service"),
                    XlsxValue.Number(4850.0),
                ),
            ),
        )
        val file = ImportReader.read(XlsxWriter.write(sheets), vocabulary)
        assertThat(file.source).isEqualTo(ImportSource.Template)
        val plan = ImportPlanner.plan(file.tables, context())
        assertThat(plan.problems).isEmpty()
        assertThat(plan.refuels.single().dateTimeUtcMs).isEqualTo(at(2026, 10, 16, 18))
        assertThat(plan.refuels.single().total).isEqualTo(Money(358_024))
        assertThat(plan.expenses.single().amount).isEqualTo(Money(485_000))
    }

    @Test
    fun `other workbooks and csv files go to the column mapper`() {
        val plain = XlsxWriter.write(
            listOf(
                app.fuelwise.core.domain.importing.xlsx.XlsxSheet(
                    "Log",
                    listOf(
                        app.fuelwise.core.domain.importing.xlsx.XlsxColumn("Date", 10.0),
                        app.fuelwise.core.domain.importing.xlsx.XlsxColumn("Amount & tax", 10.0),
                    ),
                    listOf(listOf(XlsxValue.Text("16/10/2026"), XlsxValue.Number(350.0))),
                ),
            ),
        )
        val file = ImportReader.read(plain, vocabulary)
        assertThat(file.source).isEqualTo(ImportSource.Spreadsheet)
        assertThat(file.unmapped!!.headers).containsExactly("Date", "Amount & tax").inOrder()
        assertThat(file.unmapped.samples(1)).containsExactly("350")

        val csv = ImportReader.read("Date,Amount\n16/10/2026,350\n".toByteArray(), vocabulary)
        assertThat(csv.source).isEqualTo(ImportSource.Spreadsheet)
        assertThat(csv.unmapped!!.firstRowNumber).isEqualTo(2)
    }

    @Test
    fun `a doctype in workbook xml is refused`() {
        assertThrows<IllegalArgumentException> {
            XmlReaderProbe.read("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a \"b\">]><x>&a;</x>")
        }
        assertThat(XmlReaderProbe.read("<t>a &amp; b &#x20B9;</t>")).isEqualTo("a & b ₹")
    }

    @Test
    fun `empty files are refused`() {
        assertThrows<IllegalArgumentException> { ImportReader.read(ByteArray(0), vocabulary) }
    }
}

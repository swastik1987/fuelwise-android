package app.fuelwise.core.domain.report

import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputeCostMetrics
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong
import org.junit.jupiter.api.Test

class ReportsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val reports = BuildReports(ComputeCostMetrics(), ComputeConsumption())

    private fun at(y: Int, m: Int, d: Int, h: Int = 10) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private val swift = Vehicle(
        id = "swift",
        name = "Swift",
        type = VehicleType.Car,
        tanks = listOf(Tank("swift-petrol", EnergyType.PETROL, 37.0, isPrimary = true)),
        startOdometerKm = 20_000.0,
        startOdometerAtUtcMs = at(2026, 8, 1),
    )

    private fun fill(id: String, day: Int, month: Int, odo: Double, litres: Double) = Refuel(
        id = id, vehicleId = "swift", tankId = "swift-petrol", dateTimeUtcMs = at(2026, month, day), zoneId = zone.id,
        odometerKm = odo, quantity = litres,
        unitPrice = Money(
            PRICE_PAISE,
        ),
        total = Money((litres * PRICE_PAISE).roundToLong()), isFull = true,
    )

    private val refuels = listOf(
        fill("f1", 2, 9, 20_100.0, 30.0),
        fill("f2", 16, 9, 20_550.0, 25.0),
        fill("f3", 30, 9, 21_050.0, 25.0),
        fill("f4", 12, 10, 21_400.0, 20.0),
    )
    private val expenses = listOf(
        Expense("x1", "swift", SystemCategory.Service.id, at(2026, 9, 10), zone.id, Money(520_000), 20_400.0),
        Expense("x2", "swift", SystemCategory.Parking.id, at(2026, 9, 12), zone.id, Money(6_000)),
        Expense("x3", "swift", SystemCategory.LoanEmi.id, at(2026, 9, 5), zone.id, Money(1_250_000)),
        Expense("x4", "swift", SystemCategory.Tyres.id, at(2026, 10, 1), zone.id, Money(1_800_000), 21_200.0),
    )
    private val history = VehicleHistory(swift, refuels, expenses)

    private val categories = SystemCategory.entries.mapIndexed { i, key ->
        Category(key.id, key, null, isHidden = false, includeInCostPerKm = key.includeInCostPerKm, sortOrder = i)
    }

    @Test
    fun `monthly vehicle report adds up the month`() {
        val report = reports.vehicle(
            history,
            categories,
            ReportPeriod.month(YearMonth.of(2026, 9)),
            zone,
        )
        assertThat(report.energy.single().fillUps).isEqualTo(3)
        assertThat(report.energy.single().quantity).isEqualTo(80.0)
        assertThat(report.energySpend).isEqualTo(Money(821_760))
        assertThat(report.expenseSpend).isEqualTo(Money(1_776_000))
        assertThat(report.totalSpend).isEqualTo(Money(2_597_760))
        assertThat(report.categories.first().categoryId).isEqualTo(SystemCategory.LoanEmi.id)
        assertThat(report.distance!!.km).isEqualTo(950.0)
        // Loan EMI is outside cost per km: (8,217.60 + 5,200 + 60) ÷ 950 km.
        assertThat(report.costPerKm).isEqualTo(Money(1_419))
        // f2 and f3 close tanks in September: 950 km on 50 L.
        assertThat(report.average!!.kmPerUnit).isEqualTo(19.0)
    }

    @Test
    fun `yearly report spans the calendar year and an empty month is empty`() {
        val year = reports.vehicle(history, categories, ReportPeriod.year(Year.of(2026)), zone)
        assertThat(year.energy.single().fillUps).isEqualTo(4)
        val empty = reports.vehicle(
            history,
            categories,
            ReportPeriod.month(YearMonth.of(2026, 3)),
            zone,
        )
        assertThat(empty.isEmpty).isTrue()
    }

    @Test
    fun `seller's report lists service and documents and carries no money`() {
        val documents = listOf(
            Document("d1", "swift", DocumentType.Insurance, expiryDateUtcMs = at(2027, 6, 30, 0)),
            Document("d2", "swift", DocumentType.Puc, expiryDateUtcMs = at(2027, 1, 15, 0)),
            Document("d3", null, DocumentType.Licence),
        )
        val report = reports.seller(history, emptyList(), documents, zone)
        assertThat(report.odometerKm).isEqualTo(21_400.0)
        assertThat(report.since).isEqualTo(LocalDate.of(2026, 9, 2))
        assertThat(report.fillUps).isEqualTo(4)
        assertThat(report.service.map { it.categoryId })
            .containsExactly(SystemCategory.Tyres.id, SystemCategory.Service.id).inOrder()
        assertThat(report.documents.map { it.type }).containsExactly(DocumentType.Insurance, DocumentType.Puc)
        // Prices hidden by construction: no field of the report or its lines is money (paise compile to a long).
        listOf(SellerReport::class, ServiceLine::class, DocumentLine::class).forEach { type ->
            type.java.declaredFields.forEach {
                assertThat(it.type).isNoneOf(Long::class.javaPrimitiveType, Long::class.javaObjectType)
            }
        }
    }

    @Test
    fun `business mileage multiplies business trips by the rate`() {
        fun trip(id: String, day: Int, km: Double, purpose: TripPurpose, status: TripStatus = TripStatus.Done) =
            Trip(id, "swift", at(2026, 10, day), zone.id, km, purpose, status = status, toPlace = "Client office")
        val trips = listOf(
            trip("b1", 3, 42.5, TripPurpose.Business),
            trip("b2", 9, 18.3, TripPurpose.Business),
            trip("p1", 10, 12.0, TripPurpose.Personal),
            trip("b3", 11, 7.0, TripPurpose.Business, TripStatus.Recording),
            Trip("b4", "swift", at(2026, 11, 2), zone.id, 30.0, TripPurpose.Business),
        )
        val report = reports.businessMileage(
            trips,
            null,
            ReportPeriod.month(YearMonth.of(2026, 10)),
            Money(1_050),
            zone,
        )
        assertThat(report.trips.map { it.tripId }).containsExactly("b1", "b2").inOrder()
        // ₹10.50 × 42.5 km = ₹446.25; × 18.3 km = ₹192.15.
        assertThat(report.trips.map { it.amount }).containsExactly(Money(44_625), Money(19_215)).inOrder()
        assertThat(report.totalKm).isWithin(1e-9).of(60.8)
        assertThat(report.totalAmount).isEqualTo(Money(63_840))
        assertThat(BuildReports.reimbursement(Money(333), 0.5)).isEqualTo(Money(167))
        val otherVehicle = reports.businessMileage(
            trips,
            setOf("nexon"),
            ReportPeriod.month(YearMonth.of(2026, 10)),
            Money(1_050),
            zone,
        )
        assertThat(otherVehicle.trips).isEmpty()
    }
}

private const val PRICE_PAISE = 10_272L

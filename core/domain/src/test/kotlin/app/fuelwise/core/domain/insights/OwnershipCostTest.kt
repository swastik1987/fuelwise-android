package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.Period
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

class OwnershipCostTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private val safari = Vehicle(
        "safari",
        "Safari",
        VehicleType.Car,
        listOf(Tank("diesel", EnergyType.DIESEL, 50.0, true)),
        startOdometerKm = 12.0,
        startOdometerAtUtcMs = at(2024, 1, 1),
        purchaseDate = LocalDate.of(2024, 1, 1),
        purchasePrice = Money(182_625_000),
    )

    // 2025 as the period: a full year inside the 5-year spread.
    private val year2025 = Period(at(2025, 1, 1), at(2026, 1, 1))

    private val diesel =
        Refuel(
            "r1", "safari", "diesel",
            at(
                2025,
                3,
                1,
            ),
            zone.id, 20_000.0, 40.0, Money(9_000), Money(3_60_000), isFull = true,
        )

    private fun expense(id: String, category: SystemCategory, amount: Long, day: Long) =
        Expense(id, "safari", category.id, day, zone.id, Money(amount))

    @Test
    fun `running costs and insurance, plus a year of the purchase price`() {
        val history = VehicleHistory(
            safari,
            listOf(diesel),
            listOf(expense("ins", SystemCategory.Insurance, 32_000_00, at(2025, 6, 1))),
        )
        val tco = ownershipCost(listOf(history), year2025, km = 12_000.0, years = 5, zone = zone)!!
        assertThat(tco.running).isEqualTo(Money(3_60_000 + 32_000_00))
        assertThat(tco.financedVehicles).isEqualTo(0)
        // 365 of the 1,827 days in the 5 years from 1 Jan 2024 (2024 and 2028 are leap years).
        assertThat(tco.purchaseShare).isEqualTo(Money(36_485_016))
        assertThat(tco.perKm).isEqualTo(Money((tco.total.paise + 6_000) / 12_000))
    }

    @Test
    fun `a vehicle on a loan counts its EMI instead of the spread price`() {
        val history = VehicleHistory(
            safari,
            listOf(diesel),
            listOf(
                expense("emi-feb", SystemCategory.LoanEmi, 25_000_00, at(2025, 2, 5)),
                expense("emi-mar", SystemCategory.LoanEmi, 25_000_00, at(2025, 3, 5)),
                expense("ins", SystemCategory.Insurance, 32_000_00, at(2025, 6, 1)),
            ),
        )
        val tco = ownershipCost(listOf(history), year2025, km = 12_000.0, years = 5, zone = zone)!!
        assertThat(tco.running).isEqualTo(Money(3_60_000 + 2 * 25_000_00 + 32_000_00))
        assertThat(tco.purchaseShare).isEqualTo(Money.ZERO)
        assertThat(tco.financedVehicles).isEqualTo(1)
        // A loan with no price entered still gets the card.
        val unpriced = history.copy(vehicle = safari.copy(purchasePrice = null))
        assertThat(ownershipCost(listOf(unpriced), year2025, null, 5, zone)!!.total).isEqualTo(tco.total)
    }

    @Test
    fun `a purchase expense stands in for the price and is not counted twice`() {
        val noPrice = safari.copy(purchasePrice = null, purchaseDate = null)
        val history =
            VehicleHistory(
                noPrice,
                emptyList(),
                listOf(expense("buy", SystemCategory.Purchase, 15_00_000_00, at(2025, 1, 1))),
            )
        val tco = ownershipCost(listOf(history), year2025, km = null, years = 5, zone = zone)!!
        assertThat(tco.running).isEqualTo(Money.ZERO)
        assertThat(tco.purchasePrice).isEqualTo(Money(150_000_000))
        // 365 of the 1,826 days from 1 Jan 2025.
        assertThat(tco.purchaseShare).isEqualTo(Money(29_983_571))
        assertThat(tco.perKm).isNull()
    }

    @Test
    fun `nothing once the spread is over, and no report without any price`() {
        val later = Period(at(2030, 1, 1), at(2031, 1, 1))
        assertThat(
            ownershipCost(
                listOf(VehicleHistory(safari, emptyList(), emptyList())),
                later,
                null,
                5,
                zone,
            )!!.purchaseShare,
        )
            .isEqualTo(Money.ZERO)
        val unpriced = VehicleHistory(safari.copy(purchasePrice = null), listOf(diesel), emptyList())
        assertThat(ownershipCost(listOf(unpriced), year2025, null, 5, zone)).isNull()
    }
}

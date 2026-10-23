package app.fuelwise.core.data.repository

import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.LogbookKind
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineLogbookRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-16T04:30:00Z"), ZoneOffset.UTC)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FuelwiseDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val entries = OfflineEntryRepository(db.entryDao(), UuidV7Generator(clock), clock) { "d" }
    private val logbook = OfflineLogbookRepository(db.logbookDao())
    private val zone = ZoneId.of("Asia/Kolkata")
    private lateinit var originalZone: TimeZone

    @Before
    fun setUp() = runTest {
        // Month grouping uses SQLite 'localtime', i.e. the device zone.
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        entries.saveRefuel(refuel("safari", day(2026, 9, 28), station = "Shell, HSR Layout", paid = 333_461))
        entries.saveRefuel(refuel("safari", day(2026, 10, 14), paid = 355_800))
        entries.saveRefuel(refuel("nexon", day(2026, 10, 10), paid = 18_450, charge = true))
        entries.saveExpense(
            expense("safari", day(2026, 10, 12), SystemCategory.TollFastag.id, 16_500, plaza = "Attibele"),
        )
        entries.saveExpense(
            expense("safari", day(2026, 10, 8), SystemCategory.Service.id, 485_000, notes = "Oil and filter"),
        )
    }

    @After
    fun tearDown() {
        db.close()
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun newestFirstAcrossFillUpsChargesExpensesAndTolls() = runTest {
        val rows = logbook.pagedEntries(LogbookFilter()).asSnapshot()
        assertThat(rows.map { it.kind }).containsExactly(
            LogbookKind.Fuel,
            LogbookKind.Toll,
            LogbookKind.Charge,
            LogbookKind.Expense,
            LogbookKind.Fuel,
        ).inOrder()
    }

    @Test
    fun filtersByVehicleKindAndSearch() = runTest {
        assertThat(logbook.pagedEntries(LogbookFilter(vehicleId = "nexon")).asSnapshot()).hasSize(1)
        assertThat(logbook.pagedEntries(LogbookFilter(kinds = setOf(LogbookKind.Toll))).asSnapshot().single().place)
            .isEqualTo("Attibele")
        assertThat(
            logbook.pagedEntries(LogbookFilter(query = "hsr")).asSnapshot().single().amount,
        ).isEqualTo(Money(333_461))
        assertThat(
            logbook.pagedEntries(LogbookFilter(query = "filter")).asSnapshot().single().kind,
        ).isEqualTo(LogbookKind.Expense)
    }

    @Test
    fun filtersByCategoryPaymentAndDates() = runTest {
        entries.saveExpense(
            expense("safari", day(2026, 10, 15), SystemCategory.Parking.id, 6_000)
                .copy(paymentMethod = PaymentMethod.Upi),
        )
        // A category picks expenses only: fill-ups have none.
        val service = logbook.pagedEntries(LogbookFilter(categoryIds = setOf(SystemCategory.Service.id))).asSnapshot()
        assertThat(service.map { it.amount }).containsExactly(Money(485_000))
        assertThat(
            logbook.pagedEntries(LogbookFilter(payments = setOf(PaymentMethod.Upi))).asSnapshot().single().amount,
        )
            .isEqualTo(Money(6_000))
        // 10–14 Oct: from inclusive, to exclusive (the 15th).
        val from = LocalDate.of(2026, 10, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val october = logbook.pagedEntries(LogbookFilter(fromUtcMs = from, toUtcMs = to)).asSnapshot()
        assertThat(october.map { it.amount }).containsExactly(Money(355_800), Money(16_500), Money(18_450)).inOrder()
        // Month totals follow the same filters.
        assertThat(logbook.monthTotals(LogbookFilter(fromUtcMs = from, toUtcMs = to)).first())
            .containsExactly("2026-10", Money(355_800 + 16_500 + 18_450))
    }

    @Test
    fun monthTotalsFollowTheFilter() = runTest {
        val totals = logbook.monthTotals(LogbookFilter(vehicleId = "safari")).first()
        assertThat(totals).containsExactly("2026-09", Money(333_461), "2026-10", Money(355_800 + 16_500 + 485_000))
    }

    @Test
    fun recentEntriesForHome() = runTest {
        assertThat(logbook.recentEntries("safari", limit = 3).first().map { it.amount })
            .containsExactly(Money(355_800), Money(16_500), Money(485_000)).inOrder()
    }

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun refuel(vehicleId: String, time: Long, station: String? = null, paid: Long, charge: Boolean = false) =
        Refuel(
            id = "",
            vehicleId = vehicleId,
            tankId = "$vehicleId-tank",
            dateTimeUtcMs = time,
            zoneId = zone.id,
            odometerKm = 42_000.0,
            quantity = 30.0,
            unitPrice = Money(10_000),
            total = Money(paid),
            isFull = true,
            stationName = station,
            socStartPct = if (charge) 20.0 else null,
            socEndPct = if (charge) 80.0 else null,
        )

    private fun expense(
        vehicleId: String,
        time: Long,
        category: String,
        amount: Long,
        plaza: String? = null,
        notes: String? = null,
    ) = Expense("", vehicleId, category, time, zone.id, Money(amount), tollPlaza = plaza, notes = notes)
}

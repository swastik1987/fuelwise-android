package app.fuelwise.core.domain.reminder

import app.fuelwise.core.domain.recurrence.balance
import app.fuelwise.core.domain.recurrence.dueDates
import app.fuelwise.core.domain.recurrence.nextDue
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.FastagRecharge
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Recurrence
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.RepeatUnit
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

class RemindersTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 16)
    private val clock = Clock.fixed(today.atTime(9, 30).atZone(zone).toInstant(), zone)
    private val evaluate = EvaluateReminders(clock)

    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun reminder(id: String, due: Long? = null, odo: Double? = null, notify: List<Int> = listOf(30, 15, 7, 1)) =
        Reminder(id, "swift", ReminderKind.Service, null, due, odo, notifyDaysBefore = notify)

    private val outlook = mapOf("swift" to OdometerOutlook(predictedKm = 24_000.0, kmPerDay = 40.0, windowKm = 300.0))

    @Test
    fun `date reminder notifies on its offsets and is sorted by urgency`() {
        val statuses = evaluate(
            listOf(
                reminder("later", due = date(2026, 12, 31)),
                reminder("in7", due = date(2026, 10, 23)),
                reminder("past", due = date(2026, 10, 2)),
            ),
            outlook,
            zone,
        )
        assertThat(statuses.map { it.reminder.id }).containsExactly("past", "in7", "later").inOrder()
        val in7 = statuses.single { it.reminder.id == "in7" }
        assertThat(in7.daysLeft).isEqualTo(7)
        assertThat(in7.urgency).isEqualTo(Urgency.Due)
        assertThat(in7.notifyToday).isTrue()
        // 14 days overdue: the weekly nag.
        assertThat(statuses.first().notifyToday).isTrue()
        assertThat(statuses.last().notifyToday).isFalse()
    }

    @Test
    fun `odometer reminder notifies the day it enters the due window`() {
        val entering = evaluate(listOf(reminder("o", odo = 24_280.0)), outlook, zone).single()
        assertThat(entering.kmLeft).isWithin(1e-9).of(280.0)
        assertThat(entering.urgency).isEqualTo(Urgency.Due)
        assertThat(entering.notifyToday).isTrue()
        val inside = evaluate(listOf(reminder("o", odo = 24_200.0)), outlook, zone).single()
        assertThat(inside.notifyToday).isFalse()
        val far = evaluate(listOf(reminder("o", odo = 25_000.0)), outlook, zone).single()
        assertThat(far.urgency).isEqualTo(Urgency.Soon)
        assertThat(far.predictedOdometerDate).isEqualTo(today.plusDays(25))
    }

    @Test
    fun `whichever comes first`() {
        val both = evaluate(listOf(reminder("b", due = date(2027, 6, 1), odo = 24_100.0)), outlook, zone).single()
        assertThat(both.urgency).isEqualTo(Urgency.Due)
    }

    @Test
    fun `snoozed reminders stay quiet`() {
        val snoozed = reminder("s", due = date(2026, 10, 23)).snoozed(clock.millis(), 3, zone)
        assertThat(evaluate(listOf(snoozed), outlook, zone).single().notifyToday).isFalse()
    }

    @Test
    fun `done moves a recurring reminder forward and retires a one-off`() {
        val service = reminder("svc", due = date(2026, 10, 20), odo = 24_100.0).copy(repeatMonths = 12, repeatKm = 10_000.0)
        val next = service.nextAfterCompletion(clock.millis(), 24_050.0, zone)!!
        assertThat(next.dueDateUtcMs).isEqualTo(date(2027, 10, 16))
        assertThat(next.dueOdometerKm).isEqualTo(34_050.0)
        assertThat(reminder("once", due = date(2026, 10, 20)).nextAfterCompletion(clock.millis(), null, zone)).isNull()
    }

    @Test
    fun `PUC defaults come from the rules per emission norm`() {
        val rules = ReminderRules(
            version = 1,
            presets = mapOf(ReminderKind.Service to PresetRule(12, 10_000.0, listOf(30, 7), twoWheeler = PresetRule(6, 3_000.0))),
            pucMonthsByNorm = mapOf(EmissionNorm.BS6 to 12, EmissionNorm.BS4 to 6),
            pucDefault = PresetRule(months = 6, notifyDaysBefore = listOf(15, 7, 1)),
            odometerWindowKmCar = 300.0,
            odometerWindowKmTwoWheeler = 150.0,
        )
        val car = Vehicle("c", "Swift", VehicleType.Car, listOf(Tank("t", EnergyType.PETROL, 37.0, true)), 0.0, 0, emissionNorm = EmissionNorm.BS6)
        val scooter = car.copy(type = VehicleType.Scooter, emissionNorm = null)
        assertThat(rules.defaultsFor(ReminderKind.Puc, car).months).isEqualTo(12)
        assertThat(rules.defaultsFor(ReminderKind.Puc, scooter).months).isEqualTo(6)
        assertThat(rules.defaultsFor(ReminderKind.Service, scooter).km).isEqualTo(3_000.0)
        assertThat(rules.defaultsFor(ReminderKind.Service, scooter).notifyDaysBefore).containsExactly(30, 7)
        assertThat(rules.odometerWindowKm(scooter)).isEqualTo(150.0)
    }

    @Test
    fun `monthly recurrence clamps to short months and caps the backfill`() {
        val emi = Recurrence("r", "swift", SystemCategory.LoanEmi.id, Money(1_200_000), 1, RepeatUnit.Months, date(2026, 1, 31), zone.id)
        assertThat(emi.dueDates(date(2026, 3, 31)))
            .containsExactly(date(2026, 1, 31), date(2026, 2, 28), date(2026, 3, 31)).inOrder()
        assertThat(emi.nextDue(date(2026, 10, 16))).isEqualTo(date(2026, 10, 31))
        val old = emi.copy(startUtcMs = date(2020, 1, 1))
        assertThat(old.dueDates(date(2026, 10, 16))).hasSize(24)
        assertThat(emi.copy(endUtcMs = date(2026, 2, 1)).dueDates(date(2026, 10, 16))).hasSize(1)
    }

    @Test
    fun `fastag balance counts recharges and FASTag tolls after the anchor only`() {
        val account = FastagAccount("a", "swift", "ICICI", "1234", Money(50_000), date(2026, 10, 1))
        val recharges = listOf(
            FastagRecharge("r0", "a", "swift", date(2026, 9, 20), zone.id, Money(100_000)),
            FastagRecharge("r1", "a", "swift", date(2026, 10, 5), zone.id, Money(50_000)),
        )
        val tolls = listOf(
            Expense("t1", "swift", SystemCategory.TollFastag.id, date(2026, 10, 6), zone.id, Money(23_500)),
            Expense("t2", "swift", SystemCategory.TollCash.id, date(2026, 10, 7), zone.id, Money(10_000)),
            Expense("t3", "swift", SystemCategory.TollFastag.id, date(2026, 10, 9), zone.id, Money(56_000)),
        )
        val balance = account.balance(recharges, tolls)
        assertThat(balance.estimate).isEqualTo(Money(20_500))
        assertThat(balance.isLow).isTrue()
    }
}

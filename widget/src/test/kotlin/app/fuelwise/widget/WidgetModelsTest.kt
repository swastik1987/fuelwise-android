package app.fuelwise.widget

import app.fuelwise.core.domain.engine.CostMetrics
import app.fuelwise.core.domain.engine.Interval
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.domain.summary.VehicleSummary
import app.fuelwise.core.domain.trip.TrackSummary
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WidgetModelsTest {
    private val safari = SampleVehicles.safari
    private val nexon = SampleVehicles.nexonEv
    private val ertiga = SampleVehicles.ertiga

    @Test
    fun `selected vehicle falls back to the first, and Charge prefers the selected EV`() {
        val all = listOf(safari, ertiga, nexon)
        assertThat(widgetVehicles(all, selectedId = null)).isEqualTo(WidgetVehicles(safari, nexon.id))
        assertThat(widgetVehicles(all, selectedId = "gone").selected).isEqualTo(safari)
        assertThat(widgetVehicles(all, selectedId = nexon.id)).isEqualTo(WidgetVehicles(nexon, nexon.id))
        assertThat(widgetVehicles(listOf(safari, ertiga), ertiga.id)).isEqualTo(WidgetVehicles(ertiga, null))
        assertThat(widgetVehicles(emptyList(), null)).isEqualTo(WidgetVehicles(null, null))
    }

    @Test
    fun `status shows this month's cost per km, the last tank and the vehicle's most urgent due item`() {
        val later = status(safari.id, "later", Urgency.Later, days = 200)
        val otherCar = status(ertiga.id, "ertiga-puc", Urgency.Overdue, days = -3)
        val soon = status(safari.id, "puc", Urgency.Soon, days = 12)
        val model = statusWidgetModel(safari, summary(Money(482), Interval(504.0, 30.0)), listOf(otherCar, soon, later), false)

        assertThat(model.vehicleName).isEqualTo("Safari")
        assertThat(model.costPerKm).isEqualTo(Money(482))
        assertThat(model.lastKmPerUnit).isWithin(1e-9).of(16.8)
        assertThat(model.unit).isEqualTo(QuantityUnit.LITRE)
        assertThat(model.nextDue).isEqualTo(soon)
        assertThat(costText(model)).isEqualTo("₹4.82")
    }

    @Test
    fun `nothing due when only far-off items exist`() {
        val model = statusWidgetModel(safari, summary(null, null), listOf(status(safari.id, "x", Urgency.Later, 90)), false)
        assertThat(model.nextDue).isNull()
        assertThat(costText(model)).isEqualTo("—")
    }

    @Test
    fun `hidden amounts mask the cost per km`() {
        val model = statusWidgetModel(safari, summary(Money(482), null), emptyList(), hideAmounts = true)
        assertThat(costText(model)).isEqualTo(HIDDEN_AMOUNT)
    }

    @Test
    fun `due items read by days unless the km mark is clearly nearer`() {
        assertThat(dueLabel(status(safari.id, "a", Urgency.Soon, days = 12))).isEqualTo(DueLabel.Days(12))
        assertThat(dueLabel(status(safari.id, "a", Urgency.Due, days = 0))).isEqualTo(DueLabel.Today)
        assertThat(dueLabel(status(safari.id, "a", Urgency.Overdue, days = -2))).isEqualTo(DueLabel.Overdue)
        assertThat(dueLabel(status(safari.id, "a", Urgency.Soon, days = 60, km = 400.0))).isEqualTo(DueLabel.Km(400.0))
        assertThat(dueLabel(status(safari.id, "a", Urgency.Soon, days = 20, km = 400.0))).isEqualTo(DueLabel.Days(20))
        assertThat(dueLabel(status(safari.id, "a", Urgency.Overdue, days = null, km = -50.0))).isEqualTo(DueLabel.Overdue)
    }

    @Test
    fun `trip controls start in place only with location and a vehicle`() {
        assertThat(tripWidgetModel(RecordingState.Idle, hasPermission = true, hasVehicle = true))
            .isEqualTo(TripWidgetModel.Idle(startInPlace = true))
        assertThat(tripWidgetModel(RecordingState.Idle, hasPermission = false, hasVehicle = true))
            .isEqualTo(TripWidgetModel.Idle(startInPlace = false))
        assertThat(tripWidgetModel(RecordingState.Idle, hasPermission = true, hasVehicle = false))
            .isEqualTo(TripWidgetModel.Idle(startInPlace = false))
    }

    @Test
    fun `a recording trip shows its distance and pause state`() {
        val active = RecordingState.Active(
            tripId = "t1",
            vehicleId = safari.id,
            startUtcMs = 0,
            paused = true,
            summary = TrackSummary(23.4, 2_280, 120, 72.0),
            route = emptyList(),
            activeMsBefore = 0,
            activeSinceUtcMs = null,
        )
        assertThat(tripWidgetModel(active, hasPermission = false, hasVehicle = true))
            .isEqualTo(TripWidgetModel.Recording(23.4, paused = true))
    }

    private fun status(vehicleId: String, id: String, urgency: Urgency, days: Long?, km: Double? = null) = ReminderStatus(
        reminder = Reminder(id, vehicleId, ReminderKind.Puc, title = null, dueDateUtcMs = null, dueOdometerKm = null),
        daysLeft = days,
        kmLeft = km,
        predictedOdometerDate = null,
        urgency = urgency,
        notifyToday = false,
    )

    private fun summary(costPerKm: Money?, latest: Interval?): VehicleSummary {
        val month = CostMetrics(Money(0), Money(0), Money(0), null, costPerKm, null, Money(0), Money(0))
        return VehicleSummary(
            month = month,
            previousMonth = month,
            costPerKmChange = null,
            latestInterval = latest,
            averageInterval = latest,
            recentIntervals = listOfNotNull(latest),
            fullFillCount = 0,
            lastRefuel = null,
            lastRefuelInterval = null,
            rangeLeftKm = null,
            mileageDrop = null,
            isEstimate = false,
        )
    }
}

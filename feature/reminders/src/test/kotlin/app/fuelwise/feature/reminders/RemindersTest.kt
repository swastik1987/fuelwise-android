package app.fuelwise.feature.reminders

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.reminder.EvaluateReminders
import app.fuelwise.core.domain.reminder.OdometerOutlook
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeReminderRepository
import app.fuelwise.core.testing.FakeRulesRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class RemindersTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 16, 10)), zone)
    private val reminders = FakeReminderRepository(nowUtcMs = clock.millis())

    private fun date(m: Int, d: Int) = LocalDate.of(2026, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun editor(kind: String? = null, id: String? = null) = ReminderEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("reminderId" to id, "vehicleId" to "safari", "kind" to kind)),
        reminders = reminders,
        vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari)),
        rules = FakeRulesRepository(),
        userData = FakeUserDataRepository(),
        entries = FakeEntryRepository(SampleEntries.safariRefuels),
        trips = FakeTripRepository(),
        predictions = ComputePredictions(clock),
        clock = clock,
        zoneId = zone,
    )

    @Test
    fun servicePresetStartsFromTodayAndTheOdometer() = runTest {
        val vm = editor(kind = ReminderKind.Service.name)
        val form = vm.state.first { it.kind == ReminderKind.Service }
        assertThat(form.repeatMonths).isEqualTo("12")
        assertThat(form.repeatKm).isEqualTo("10000")
        assertThat(
            form.dueDateUtcMs,
        ).isEqualTo(LocalDate.of(2027, 10, 16).atStartOfDay(zone).toInstant().toEpochMilli())
        assertThat(form.dueOdometer.toDouble()).isGreaterThan(54_000.0)
    }

    @Test
    fun legalRenewalsWaitForTheCertificateDate() = runTest {
        val vm = editor(kind = ReminderKind.Insurance.name)
        val form = vm.state.first { it.kind == ReminderKind.Insurance }
        assertThat(form.dueDateUtcMs).isNull()
        assertThat(form.notifyDaysBefore).containsExactly(30, 15, 7, 1)
        vm.save()
        assertThat(vm.state.value.missingDue).isTrue()
        vm.onDueDate(date(11, 30))
        vm.save()
        val event = vm.events.first() as ReminderEditorEvent.Saved
        assertThat(event.firstReminder).isTrue()
        assertThat(reminders.reminders.single().dueDateUtcMs).isEqualTo(date(11, 30))
    }

    @Test
    fun customNeedsATitle() = runTest {
        val vm = editor(kind = ReminderKind.Custom.name)
        vm.state.first { it.kind == ReminderKind.Custom }
        vm.onDueDate(date(10, 30))
        vm.save()
        assertThat(vm.state.value.missingTitle).isTrue()
    }

    @Test
    fun markDoneMovesARecurringReminder() = runTest {
        reminders.save(Reminder("svc", "safari", ReminderKind.Service, null, date(10, 20), null, repeatMonths = 6))
        val vm = editor(id = "svc")
        vm.state.first { it.isEdit }
        vm.markDone()
        vm.events.first()
        // Done on 16 Oct 2026, every 6 months: next due 16 Apr 2027.
        val expected = LocalDate.of(2027, 4, 16).atStartOfDay(zone).toInstant().toEpochMilli()
        assertThat(reminders.reminders.single().dueDateUtcMs).isEqualTo(expected)
    }

    private val sample = listOf(
        Reminder("a", "safari", ReminderKind.Puc, null, date(10, 12), null),
        Reminder("b", "safari", ReminderKind.Insurance, null, date(10, 23), null),
        Reminder("c", "safari", ReminderKind.Service, null, date(12, 20), 52_500.0),
        Reminder("d", "safari", ReminderKind.Custom, "Wheel alignment", null, 60_000.0),
    )

    private val state: RemindersUiState
        get() = RemindersUiState(
            loading = false,
            vehicles = listOf(SampleVehicles.safari),
            upcoming = EvaluateReminders(clock)(
                sample,
                mapOf("safari" to OdometerOutlook(predictedKm = 52_350.0, kmPerDay = 35.0, windowKm = 300.0)),
                zone,
            ),
        )

    @Test fun listLight() = capture("reminders_light", false) { RemindersContent(state, false, {}, {}, {}, {}) }

    @Test fun listDark() = capture("reminders_dark", true) { RemindersContent(state, true, {}, {}, {}, {}) }

    @Test fun presetsLight() = capture("reminder_presets_light", false) {
        ReminderEditorContent(ReminderForm(loading = false, vehicle = SampleVehicles.safari), ReminderEditorActions())
    }

    @Test fun formDark() = capture("reminder_form_dark", true) {
        ReminderEditorContent(
            ReminderForm(
                loading = false,
                vehicle = SampleVehicles.safari,
                kind = ReminderKind.Puc,
                dueDateUtcMs = date(12, 1),
                repeatMonths = "12",
                notifyDaysBefore = setOf(15, 7, 1),
            ),
            ReminderEditorActions(),
        )
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

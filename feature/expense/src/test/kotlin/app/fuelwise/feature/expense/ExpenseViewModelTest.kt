package app.fuelwise.feature.expense

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.photo.EntryPhotoDraft
import app.fuelwise.core.data.photo.PickedPhotoReader
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.designsystem.component.KeypadKey
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.Reminder
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.RepeatUnit
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeDocumentRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeRecurrenceRepository
import app.fuelwise.core.testing.FakeReminderRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExpenseViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val entries = FakeEntryRepository()
    private val recurrences = FakeRecurrenceRepository()
    private val categories = FakeCategoryRepository()

    private val reminders = FakeReminderRepository()
    private val documents = FakeDocumentRepository()
    private val mysuru = Trip(
        "mysuru",
        "safari",
        Instant.parse("2026-10-16T01:30:00Z").toEpochMilli(),
        "Asia/Kolkata",
        146.0,
        TripPurpose.Personal,
        endUtcMs = Instant.parse("2026-10-16T05:00:00Z").toEpochMilli(),
        fromPlace = "HSR Layout",
        toPlace = "Mysuru",
    )
    private val trips = FakeTripRepository(listOf(mysuru))

    private fun viewModel(toll: Boolean = false, extra: Map<String, Any?> = emptyMap()) = ExpenseViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf("vehicleId" to "safari", "expenseId" to null, "toll" to toll) + extra,
        ),
        vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari)),
        categories = categories,
        entries = entries,
        userData = FakeUserDataRepository(),
        recurrences = recurrences,
        reminders = reminders,
        trips = trips,
        photos = EntryPhotoDraft(documents),
        picked = PickedPhotoReader(ApplicationProvider.getApplicationContext(), main.dispatcher),
        io = main.dispatcher,
        clock = Clock.fixed(Instant.parse("2026-10-16T04:30:00Z"), ZoneOffset.UTC),
        zoneId = ZoneId.of("Asia/Kolkata"),
    )

    private fun ExpenseViewModel.typeAmount(text: String) {
        onFocus(ExpenseField.Amount)
        text.forEach { c -> onKey(if (c == '.') KeypadKey.Dot else KeypadKey.Digit(c.digitToInt())) }
    }

    @Test
    fun needsACategoryAndAnAmount() = runTest {
        val vm = viewModel()
        vm.save()
        assertThat(vm.state.value.amountMissing).isTrue()
        assertThat(vm.state.value.categoryMissing).isTrue()
        assertThat(entries.expenses).isEmpty()
    }

    @Test
    fun savesAServiceExpense() = runTest {
        val vm = viewModel()
        vm.onCategory(SystemCategory.Service.id)
        vm.typeAmount("4850")
        vm.onPayment(PaymentMethod.Upi)
        vm.save()
        vm.saved.first()
        val saved = entries.expenses.single()
        assertThat(saved.amount).isEqualTo(Money(485_000))
        assertThat(saved.categoryId).isEqualTo(SystemCategory.Service.id)
        assertThat(saved.tollPlaza).isNull()
    }

    @Test
    fun tollMapsFastagOrCashToItsCategoryAndKeepsThePlaza() = runTest {
        val vm = viewModel(toll = true)
        vm.onPlaza("Attibele")
        vm.typeAmount("165")
        vm.save()
        vm.saved.first()
        val fastag = entries.expenses.single()
        assertThat(fastag.categoryId).isEqualTo(SystemCategory.TollFastag.id)
        assertThat(fastag.paymentMethod).isEqualTo(PaymentMethod.FastagWallet)
        assertThat(fastag.tollPlaza).isEqualTo("Attibele")

        val cash = viewModel(toll = true)
        cash.onTollMethod(fastag = false)
        cash.typeAmount("230")
        cash.save()
        cash.saved.first()
        assertThat(entries.expenses.last().categoryId).isEqualTo(SystemCategory.TollCash.id)
    }

    @Test
    fun repeatStartsTheRecurrenceAtTheNextDueDate() = runTest {
        val vm = viewModel()
        vm.onCategory(SystemCategory.LoanEmi.id)
        vm.typeAmount("12000")
        vm.onRepeat(true)
        vm.save()
        vm.saved.first()
        assertThat(entries.expenses).hasSize(1)
        val recurrence = recurrences.saved.single()
        assertThat(recurrence.amount).isEqualTo(Money(1_200_000))
        assertThat(recurrence.unit).isEqualTo(RepeatUnit.Months)
        // Saved on 16 Oct (IST): the first auto entry is 16 Nov, so this one is never created twice.
        assertThat(recurrence.startUtcMs).isEqualTo(Instant.parse("2026-11-15T18:30:00Z").toEpochMilli())
    }

    @Test
    fun addCostAfterDoneStartsOnItsCategoryAndLinksTheExpense() = runTest {
        val reminderId = reminders.save(Reminder("", "safari", ReminderKind.Puc, null, 0L, null))
        val completionId = reminders.complete(reminderId, odometerKm = null)!!
        val vm = viewModel(extra = mapOf("categoryId" to SystemCategory.Puc.id, "completionId" to completionId))
        assertThat(vm.state.first { !it.loading }.categoryId).isEqualTo(SystemCategory.Puc.id)
        vm.typeAmount("120")
        vm.save()
        vm.saved.first()
        val puc = entries.expenses.single()
        assertThat(puc.categoryId).isEqualTo(SystemCategory.Puc.id)
        assertThat(reminders.completion(completionId)!!.expenseId).isEqualTo(puc.id)
    }

    @Test
    fun aTollLinksToTheTripItWasPaidOn() = runTest {
        val vm = viewModel(toll = true)
        // Saved at 10:00 IST on 16 Oct, during the drive to Mysuru.
        assertThat(vm.state.first { it.trips.isNotEmpty() }.trips.map { it.id }).containsExactly("mysuru")
        vm.onTrip("mysuru")
        vm.onPlaza("Kaniminike")
        vm.typeAmount("165")
        vm.save()
        vm.saved.first()
        assertThat(entries.expenses.single().tripId).isEqualTo("mysuru")
        // Tapping it again unlinks.
        vm.onTrip("mysuru")
        assertThat(vm.state.value.tripId).isNull()
    }

    @Test
    fun editingKeepsSavedPhotosAndOnlyRemovesOnSave() = runTest {
        val receipt = File.createTempFile("bill", ".jpg").apply {
            outputStream().use { createBitmap(1200, 1600).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val vm = viewModel()
        vm.onCategory(SystemCategory.Service.id)
        vm.typeAmount("4850")
        vm.onPhotosPicked(listOf(Uri.fromFile(receipt)))
        vm.state.first { it.photos.isNotEmpty() }
        vm.save()
        vm.saved.first()
        val service = entries.expenses.single()
        val photo = documents.allAttachments().single()
        assertThat(photo.ownerId).isEqualTo(service.id)
        assertThat(photo.ownerType).isEqualTo(Attachment.OwnerType.Expense)

        val edit = viewModel(extra = mapOf("expenseId" to service.id))
        assertThat(edit.state.first { !it.loading }.isEdit).isTrue()
        edit.onRemovePhoto(photo.id)
        // Removing is only noted until save.
        assertThat(documents.allAttachments()).hasSize(1)
        edit.save()
        edit.saved.first()
        assertThat(documents.allAttachments()).isEmpty()
    }
}

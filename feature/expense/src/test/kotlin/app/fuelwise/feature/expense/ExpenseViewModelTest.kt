package app.fuelwise.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.designsystem.component.KeypadKey
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExpenseViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val entries = FakeEntryRepository()
    private val categories = object : CategoryRepository {
        override fun observeCategories() = flowOf(
            SystemCategory.entries.mapIndexed { i, c -> Category(c.id, c, null, false, c.includeInCostPerKm, i) },
        )
    }

    private fun viewModel(toll: Boolean = false) = ExpenseViewModel(
        savedStateHandle = SavedStateHandle(mapOf("vehicleId" to "safari", "expenseId" to null, "toll" to toll)),
        vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari)),
        categories = categories,
        entries = entries,
        userData = FakeUserDataRepository(),
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
}

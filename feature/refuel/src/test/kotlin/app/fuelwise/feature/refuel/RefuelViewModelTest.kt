package app.fuelwise.feature.refuel

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.component.KeypadKey
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.entry.CheckFillUp
import app.fuelwise.core.domain.entry.ComputeFillReward
import app.fuelwise.core.domain.entry.FillReward
import app.fuelwise.core.domain.entry.PriceTriangle
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceSource
import app.fuelwise.core.model.Refuel
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
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RefuelViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = Instant.parse("2026-10-16T04:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari, SampleVehicles.nexonEv))
    private val entries = FakeEntryRepository()

    private fun viewModel(vehicleId: String = "safari", refuelId: String? = null) = RefuelViewModel(
        savedStateHandle = SavedStateHandle(mapOf("vehicleId" to vehicleId, "refuelId" to refuelId)),
        vehicles = vehicles,
        entries = entries,
        userData = FakeUserDataRepository(),
        checkFillUp = CheckFillUp(),
        computeReward = ComputeFillReward(ComputeConsumption()),
        predictions = ComputePredictions(clock),
        clock = clock,
        zoneId = ZoneId.of("Asia/Kolkata"),
    )

    private fun RefuelViewModel.type(field: NumericField, text: String) {
        onFocus(field)
        text.forEach { c -> onKey(if (c == '.') KeypadKey.Dot else KeypadKey.Digit(c.digitToInt())) }
    }

    @Test
    fun typingQuantityAndPriceComputesTheTotal() = runTest {
        val vm = viewModel()
        vm.type(NumericField.Quantity, "32.40")
        vm.type(NumericField.UnitPrice, "102.92")
        assertThat(vm.state.value.computedField).isEqualTo(PriceTriangle.Field.Total)
        assertThat(vm.state.value.total).isEqualTo("3334.61")

        vm.type(NumericField.Total, "") // focusing alone doesn't change the lock
        vm.onKey(KeypadKey.Backspace) // editing the total now locks it with price; quantity is computed
        assertThat(vm.state.value.computedField).isEqualTo(PriceTriangle.Field.Quantity)
    }

    @Test
    fun lastPricePaidIsPrefilledAndLocked() = runTest {
        entries.saveRefuel(fill("a", odo = 41_900.0, price = 8_895))
        val vm = viewModel()
        assertThat(vm.state.value.unitPrice).isEqualTo("88.95")
        assertThat(vm.state.value.priceSource).isEqualTo(PriceSource.LastPaid)
        vm.type(NumericField.Total, "3558")
        assertThat(vm.state.value.quantity).isEqualTo("40.00")
    }

    @Test
    fun odometerBelowThePreviousEntryBlocksSaving() = runTest {
        entries.saveRefuel(fill("a", odo = 42_000.0, time = now.toEpochMilli() - DAY))
        val vm = viewModel()
        vm.type(NumericField.Odometer, "41500")
        vm.type(NumericField.Quantity, "40")
        vm.type(NumericField.Total, "3558")
        vm.save()
        assertThat(vm.state.value.problems).hasSize(1)
        assertThat(entries.refuels).hasSize(1)
    }

    @Test
    fun missingFieldsAreReportedOnlyAfterSave() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.missing).isEmpty()
        vm.save()
        assertThat(
            vm.state.value.missing,
        ).containsExactly(RequiredField.Odometer, RequiredField.Quantity, RequiredField.Total)
    }

    @Test
    fun firstFullFillShowsBaselineThenTheNextShowsMileage() = runTest {
        val first = viewModel()
        first.type(NumericField.Odometer, "42318")
        first.type(NumericField.Quantity, "45")
        first.type(NumericField.Total, "4002.75")
        first.save()
        assertThat(first.state.value.reward).isEqualTo(FillReward.Baseline)

        entries.saveRefuel(entries.refuels.single().copy(dateTimeUtcMs = now.toEpochMilli() - DAY))
        val second = viewModel()
        second.type(NumericField.Odometer, "42918")
        second.type(NumericField.Quantity, "40")
        second.save()
        val reward = second.state.value.reward as FillReward.Measured
        assertThat(reward.interval.kmPerUnit).isWithin(1e-9).of(15.0)
    }

    @Test
    fun evVehicleOpensTheChargeVariantAndSavesSoc() = runTest {
        val vm = viewModel(vehicleId = "nexon")
        assertThat(vm.state.value.isCharge).isTrue()
        assertThat(vm.state.value.charger).isEqualTo(ChargerType.HomeAc)
        vm.type(NumericField.Odometer, "18402")
        vm.type(NumericField.Quantity, "24.6")
        vm.type(NumericField.UnitPrice, "7.5")
        vm.onSoc(22f, 100f)
        vm.save()
        val saved = entries.refuels.single()
        assertThat(saved.total).isEqualTo(Money(18_450))
        assertThat(saved.socStartPct).isEqualTo(22.0)
        assertThat(saved.isFull).isTrue() // 100% counts as a full charge
    }

    private fun fill(id: String, odo: Double, price: Long = 10_292, time: Long = now.toEpochMilli() - 10 * DAY) =
        Refuel(
            id = id,
            vehicleId = "safari",
            tankId = "safari-diesel",
            dateTimeUtcMs = time,
            zoneId = "Asia/Kolkata",
            odometerKm = odo,
            quantity = 40.0,
            unitPrice = Money(price),
            total = Money(price * 40),
            isFull = true,
        )

    private companion object {
        const val DAY = 86_400_000L
    }
}

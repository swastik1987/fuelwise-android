package app.fuelwise.feature.refuel

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.vault.ScanPhotoStash
import app.fuelwise.core.designsystem.component.KeypadKey
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.entry.CheckFillUp
import app.fuelwise.core.domain.entry.ComputeFillReward
import app.fuelwise.core.domain.entry.FillReward
import app.fuelwise.core.domain.entry.PriceTriangle
import app.fuelwise.core.domain.prices.FairPrice
import app.fuelwise.core.model.Attachment
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import app.fuelwise.core.model.PriceSource
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.ScanPrefill
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.testing.FakeDocumentRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeFuelPriceRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleVehicles
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
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
    private val documents = FakeDocumentRepository()
    private val stash = ScanPhotoStash({ "photo-1" }, documents)
    private lateinit var handle: SavedStateHandle

    private fun viewModel(
        vehicleId: String = "safari",
        refuelId: String? = null,
        trips: FakeTripRepository = FakeTripRepository(),
        cityPrices: FakeFuelPriceRepository = FakeFuelPriceRepository(),
        userData: FakeUserDataRepository = FakeUserDataRepository(),
        scan: String? = null,
    ) = RefuelViewModel(
        savedStateHandle = SavedStateHandle(mapOf("vehicleId" to vehicleId, "refuelId" to refuelId, "scan" to scan))
            .also { handle = it },
        vehicles = vehicles,
        entries = entries,
        trips = trips,
        cityPrices = cityPrices,
        userData = userData,
        checkFillUp = CheckFillUp(),
        computeReward = ComputeFillReward(ComputeConsumption()),
        predictions = ComputePredictions(clock),
        clock = clock,
        zoneId = ZoneId.of("Asia/Kolkata"),
        scanPhotos = stash,
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
    fun aNewerHomeCityPriceIsPrefilledAndTheSaveSaysIfItWasFair() = runTest {
        entries.saveRefuel(fill("a", odo = 41_900.0, price = 8_850))
        val today = LocalDate.of(2026, 10, 16)
        val cityPrices = FakeFuelPriceRepository(
            listOf(FuelPrice("p1", "bengaluru", PriceFuel.Diesel, today.minusDays(2), Money(8_895))),
        )
        val userData = FakeUserDataRepository(FakeUserDataRepository.DEFAULT.copy(homeCityId = "bengaluru"))
        val vm = viewModel(cityPrices = cityPrices, userData = userData)
        val ready = vm.state.first { it.unitPrice.isNotEmpty() }
        assertThat(ready.unitPrice).isEqualTo("88.95")
        assertThat(ready.priceSource).isEqualTo(PriceSource.CityPrice)

        // Paying the prefilled city price is "the city price"; above/below is covered in core:domain FairPriceTest.
        vm.type(NumericField.Odometer, "42318")
        vm.type(NumericField.Total, "3558")
        vm.save()
        val fair = vm.state.first { it.reward != null }.fairPrice!!
        assertThat(fair.city.price).isEqualTo(Money(8_895))
        assertThat(fair.verdict).isEqualTo(FairPrice.Verdict.Same)
    }

    @Test
    fun withoutAHomeCityThereIsNoFairPriceLine() = runTest {
        val vm = viewModel(
            cityPrices = FakeFuelPriceRepository(
                listOf(FuelPrice("p1", "bengaluru", PriceFuel.Diesel, LocalDate.of(2026, 10, 16), Money(8_895))),
            ),
        )
        vm.type(NumericField.Odometer, "42318")
        vm.type(NumericField.Quantity, "40")
        vm.type(NumericField.Total, "3558")
        vm.save()
        assertThat(vm.state.first { it.reward != null }.fairPrice).isNull()
    }

    @Test
    fun aFinishedTripMovesTheOdometerHint() = runTest {
        entries.saveRefuel(fill("a", odo = 42_500.0, time = now.toEpochMilli() - 10 * DAY))
        assertThat(viewModel().state.first { it.predictedOdometer != null }.predictedOdometer!!).isLessThan(42_600.0)
        val trip = Trip(
            id = "t1",
            vehicleId = "safari",
            startUtcMs = now.toEpochMilli() - 2 * DAY,
            endUtcMs = now.toEpochMilli() - DAY,
            zoneId = "Asia/Kolkata",
            distanceKm = 140.0,
            purpose = TripPurpose.Personal,
            startOdometerKm = 42_860.0,
            endOdometerKm = 43_000.0,
            source = TripSource.Gps,
        )
        val withTrip = viewModel(trips = FakeTripRepository(listOf(trip)))
        assertThat(withTrip.state.first { it.predictedOdometer != null }.predictedOdometer!!).isAtLeast(43_000.0)
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

    @Test
    fun homeChargesStartFromTheSettingsTariff() = runTest {
        // PRD F-02.5: the Settings home tariff (₹8.20/kWh) fills Home AC charges; public chargers keep their own.
        val userData = FakeUserDataRepository().apply { setHomeTariff(Money(820)) }
        val vm = viewModel(vehicleId = "nexon", userData = userData)
        val home = vm.state.first { it.unitPrice.isNotEmpty() }
        assertThat(home.charger).isEqualTo(ChargerType.HomeAc)
        assertThat(home.unitPrice).isEqualTo("8.20")
        assertThat(home.homeTariffPrice).isTrue()

        vm.onCharger(ChargerType.DcFast)
        assertThat(vm.state.value.homeTariffPrice).isFalse()
        vm.onCharger(ChargerType.HomeAc)
        assertThat(vm.state.value.unitPrice).isEqualTo("8.20")
        assertThat(vm.state.value.homeTariffPrice).isTrue()

        vm.type(NumericField.UnitPrice, "9")
        assertThat(vm.state.value.homeTariffPrice).isFalse()
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

    @Test
    fun aValidatedScanFillsTheTriangleAsReadAndAttachesThePhotoOnSave() = runTest {
        val token = stash.put(byteArrayOf(7, 7))
        val scan = ScanPrefill(
            total = Money(200_000),
            quantity = 19.43,
            unitPrice = Money(10_292),
            odometerKm = 42_318,
            photoToken = token,
        )
        val vm = viewModel(scan = scan.encode())
        val s = vm.state.value
        assertThat(s.quantity).isEqualTo("19.43")
        assertThat(s.unitPrice).isEqualTo("102.92")
        assertThat(s.total).isEqualTo("2000")
        assertThat(s.odometer).isEqualTo("42318")
        assertThat(s.priceSource).isEqualTo(PriceSource.Scanned)
        assertThat(s.scanPhotoToken).isEqualTo(token)

        vm.save()
        val saved = entries.observeRefuels("safari").first().single()
        val photos = documents.observeAttachments(saved.id).first()
        assertThat(photos.single().ownerType).isEqualTo(Attachment.OwnerType.Refuel)
        assertThat(stash.peek(token)).isNull()
    }

    @Test
    fun aPartialScanKeepsTypedFieldsAndComputesTheThird() = runTest {
        val vm = viewModel()
        vm.type(NumericField.Odometer, "42318")
        vm.type(NumericField.Quantity, "19.43")
        // A scan handed back to the open sheet (banner flow) after the user typed.
        applyScanForTest(ScanPrefill(unitPrice = Money(10_292)))
        val s = vm.state.value
        assertThat(s.odometer).isEqualTo("42318")
        assertThat(s.total).isEqualTo("1999.74")
        assertThat(s.priceSource).isEqualTo(PriceSource.Scanned)
    }

    @Test
    fun removingTheScanPhotoSavesWithoutIt() = runTest {
        val token = stash.put(byteArrayOf(1))
        val vm = viewModel(scan = ScanPrefill(total = Money(200_000), quantity = 19.43, photoToken = token).encode())
        vm.type(NumericField.Odometer, "42318")
        vm.onRemoveScanPhoto()
        vm.save()
        val saved = entries.observeRefuels("safari").first().single()
        assertThat(documents.observeAttachments(saved.id).first()).isEmpty()
    }

    private fun applyScanForTest(scan: ScanPrefill) = handle.set("scan", scan.encode())

    private companion object {
        const val DAY = 86_400_000L
    }
}

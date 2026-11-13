package app.fuelwise.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.data.vault.ScanPhotoStash
import app.fuelwise.core.domain.scan.OcrLine
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.ScanPrefill
import app.fuelwise.core.testing.FakeDocumentRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeScanSettingsRepository
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
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScanViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val stash = ScanPhotoStash({ "photo-1" }, FakeDocumentRepository())
    private val photo = byteArrayOf(1, 2, 3)

    private fun viewModel(mode: ScanMode = ScanMode.PumpMeter) = ScanViewModel(
        savedStateHandle = SavedStateHandle(mapOf("mode" to mode.name, "vehicleId" to "safari")),
        vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari)),
        entries = FakeEntryRepository(),
        userData = FakeUserDataRepository(),
        settings = FakeScanSettingsRepository(),
        stash = stash,
        clock = Clock.fixed(Instant.parse("2026-10-16T13:00:00Z"), ZoneOffset.UTC),
        zoneId = ZoneId.of("Asia/Kolkata"),
        parsing = main.dispatcher,
    )

    private fun frame(vararg text: String) = OcrFrame(text.map { OcrLine(it) }, 1280, 720) { photo }

    private val pump = frame("AMOUNT ₹ 2000.00", "VOLUME L 19.43", "RATE ₹/L 102.92")

    @Test
    fun twoMatchingFramesOpenTheConfirmSheetWithEveryFieldTicked() = runTest {
        val vm = viewModel()
        vm.onFrame(pump)
        assertThat(vm.state.value.allValid).isTrue()
        assertThat(vm.state.value.review).isNull()
        vm.onFrame(pump)
        val review = requireNotNull(vm.state.value.review)
        assertThat(review.validated).isTrue()
        assertThat(review.confirmed).containsExactly(ScanField.Total, ScanField.Quantity, ScanField.Rate)

        vm.onUse()
        val done = vm.eventFlow.first() as ScanViewModel.Event.Done
        assertThat(done.prefill).isEqualTo(
            ScanPrefill(total = Money(200_000), quantity = 19.43, unitPrice = Money(10_292), photoToken = "photo-1"),
        )
        assertThat(stash.peek("photo-1")).isEqualTo(photo)
    }

    @Test
    fun anInconsistentReadingNeedsEachFieldTicked() = runTest {
        val vm = viewModel()
        vm.onFrame(frame("AMOUNT 2000.00", "VOLUME 19.48", "RATE 102.92"))
        assertThat(vm.state.value.allValid).isFalse()
        vm.onReadNow()
        val review = requireNotNull(vm.state.value.review)
        assertThat(review.validated).isFalse()
        assertThat(review.confirmed).isEmpty()

        vm.onUse() // Nothing ticked: nothing happens.
        vm.onToggleField(ScanField.Total)
        vm.onToggleField(ScanField.Rate)
        vm.onAttachPhoto(false)
        vm.onUse()
        val done = vm.eventFlow.first() as ScanViewModel.Event.Done
        assertThat(done.prefill).isEqualTo(ScanPrefill(total = Money(200_000), unitPrice = Money(10_292)))
    }

    @Test
    fun odometerScanCarriesOnlyTheReadingAndNoPhoto() = runTest {
        val vm = viewModel(ScanMode.Odometer)
        vm.onFrame(frame("TRIP A 123.4 km", "ODO 42318 km"))
        vm.onFrame(frame("TRIP A 123.4 km", "ODO 42318 km"))
        val review = requireNotNull(vm.state.value.review)
        assertThat(review.hasPhoto).isFalse()
        vm.onUse()
        val done = vm.eventFlow.first() as ScanViewModel.Event.Done
        assertThat(done.prefill).isEqualTo(ScanPrefill(odometerKm = 42_318))
    }

    @Test
    fun readNowWithNothingReadShowsTheFailureState() = runTest {
        val vm = viewModel()
        vm.onFrame(frame("AMOUNT", "VOLUME"))
        vm.onReadNow()
        assertThat(vm.state.value.review).isNull()
        assertThat(vm.state.value.struggling).isTrue()
    }

    @Test
    fun rescanAndModeChangeClearTheReading() = runTest {
        val vm = viewModel()
        vm.onFrame(pump)
        vm.onFrame(pump)
        vm.onRescan()
        assertThat(vm.state.value.review).isNull()
        assertThat(vm.state.value.hasReading).isFalse()
        vm.onMode(ScanMode.Receipt)
        assertThat(vm.state.value.mode).isEqualTo(ScanMode.Receipt)
    }

    @Test
    fun deniedCameraIsRemembered() = runTest {
        val vm = viewModel()
        vm.onCameraAccess(false)
        assertThat(vm.state.value.camera).isEqualTo(CameraAccess.Denied)
    }

    @Test
    fun aMissedLiveFrameBetweenTwoMatchingReadingsStillConfirms() = runTest {
        // Live frames take turns with image clean-ups; one may read nothing useful.
        val vm = viewModel()
        vm.onFrame(pump)
        vm.onFrame(frame("ENSURE ZERO BEFORE DELIVERY"))
        assertThat(vm.state.value.review).isNull()
        vm.onFrame(pump)
        assertThat(requireNotNull(vm.state.value.review).validated).isTrue()
    }

    @Test
    fun aSnapThatChecksOutOpensTheTickedSheetAndPausesLiveFrames() = runTest {
        val vm = viewModel()
        vm.onSnapStarted()
        assertThat(vm.state.value.snapping).isTrue()
        vm.onFrame(pump)
        assertThat(vm.state.value.hasReading).isFalse()

        // The owner's Gilbarco photo: the plain read misses the rate, a cleaned-up read gets all three.
        vm.onSnap(
            OcrSnap(
                reads = listOf(
                    lines("ENSURE ZERO BEFORE DELIVERY", "2887.14", "29.32", "Litre"),
                    lines("2887.14 ₹", "29.32", "Litre", "821.4", "Density Kg/m3", "98.47", "₹/Litre"),
                ),
                width = 1125,
                height = 2000,
            ) { photo },
        )
        val s = vm.state.value
        assertThat(s.snapping).isFalse()
        val review = requireNotNull(s.review)
        assertThat(review.validated).isTrue()
        assertThat(review.hasPhoto).isTrue()
        assertThat(review.values).isEqualTo(
            ScanPrefill(total = Money(288_714), quantity = 29.32, unitPrice = Money(9_847)),
        )
    }

    @Test
    fun partialSnapReadsCombine() = runTest {
        val vm = viewModel()
        vm.onSnapStarted()
        vm.onSnap(
            OcrSnap(listOf(lines("2887.14", "29.32 Litre"), lines("Density 821.4", "98.47 ₹/Litre")), 1125, 2000) {
                photo
            },
        )
        assertThat(requireNotNull(vm.state.value.review).validated).isTrue()
    }

    @Test
    fun aSnapThatReadsNothingShowsTheFailureState() = runTest {
        val vm = viewModel()
        vm.onSnapStarted()
        vm.onSnap(OcrSnap.Nothing)
        val s = vm.state.value
        assertThat(s.snapping).isFalse()
        assertThat(s.review).isNull()
        assertThat(s.struggling).isTrue()
    }

    private fun lines(vararg text: String) = text.map { OcrLine(it) }
}

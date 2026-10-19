package app.fuelwise.feature.trips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.domain.engine.ComputeConsumption
import app.fuelwise.core.domain.trip.EstimateTripCost
import app.fuelwise.core.domain.trip.TrackSummary
import app.fuelwise.core.domain.trip.TripCostBasis
import app.fuelwise.core.domain.trip.TripCostEstimate
import app.fuelwise.core.domain.trip.TripSplit
import app.fuelwise.core.location.RecordingHost
import app.fuelwise.core.location.RecordingState
import app.fuelwise.core.location.TripLocationSource
import app.fuelwise.core.location.TripRecorder
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.FakeEntryRepository
import app.fuelwise.core.testing.FakeTripRepository
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.FakeVehicleRepository
import app.fuelwise.core.testing.MainDispatcherRule
import app.fuelwise.core.testing.SampleEntries
import app.fuelwise.core.testing.SampleVehicles
import app.fuelwise.core.ui.map.LocalMapsEnabled
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// runCurrent drives the recorder's background work on the test scheduler.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class TripsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.ofEpochMilli(SampleEntries.at(2026, 10, 16)), zone)
    private val estimate = EstimateTripCost(ComputeConsumption())
    private val vehicles = FakeVehicleRepository(listOf(SampleVehicles.safari, SampleVehicles.ertiga))
    private val userData = FakeUserDataRepository(FakeUserDataRepository.DEFAULT.copy(selectedVehicleId = "safari"))
    private val mysuru = Trip(
        id = "mysuru",
        vehicleId = "safari",
        startUtcMs = SampleEntries.at(2026, 10, 12, hour = 7),
        endUtcMs = SampleEntries.at(2026, 10, 12, hour = 10),
        zoneId = zone.id,
        distanceKm = 146.0,
        purpose = TripPurpose.Personal,
        startOdometerKm = 44_320.0,
        endOdometerKm = 44_466.0,
        fromPlace = "Bengaluru",
        toPlace = "Mysuru",
        estimatedFuelCost = Money(89_600),
    )
    private val toll = Expense(
        "t1",
        "safari",
        SystemCategory.TollFastag.id,
        SampleEntries.at(2026, 10, 12, hour = 8),
        zone.id,
        Money(23_000),
        tollPlaza = "Kaniminike",
        tripId = "mysuru",
    )

    private fun editor(trips: FakeTripRepository, tripId: String? = null) = TripEditorViewModel(
        SavedStateHandle(mapOf("tripId" to tripId)),
        trips,
        vehicles,
        FakeEntryRepository(refuels = SampleEntries.safariRefuels),
        userData,
        estimate,
        clock,
        zone,
    )

    private fun detail(trips: FakeTripRepository) = TripDetailViewModel(
        SavedStateHandle(mapOf("tripId" to "mysuru")),
        trips,
        vehicles,
        FakeEntryRepository(refuels = SampleEntries.safariRefuels, expenses = listOf(toll)),
        FakeCategoryRepository(),
        userData,
        estimate,
    )

    @Test
    fun manualTripFromOdometersGetsAFuelEstimate() = runTest {
        val trips = FakeTripRepository()
        val vm = editor(trips)
        vm.state.first { !it.loading }
        vm.onStartOdometer("44320")
        vm.onEndOdometer("44466")
        vm.onPurpose(TripPurpose.Business)
        vm.onFrom("Bengaluru")
        vm.onTo("Mysuru")
        assertThat(vm.state.value.odometerDistanceKm).isEqualTo(146.0)
        vm.save()
        vm.saved.first()
        val saved = trips.trips.single()
        assertThat(saved.distanceKm).isEqualTo(146.0)
        assertThat(saved.purpose).isEqualTo(TripPurpose.Business)
        // Safari's own history: 1,910 km on 123.7 L = 15.44 km/L; 146 km ≈ 9.46 L × ₹89.30 (last price paid).
        assertThat(saved.estimatedFuelCost).isEqualTo(Money(84_439))
    }

    @Test
    fun saveNeedsADistance() = runTest {
        val trips = FakeTripRepository()
        val vm = editor(trips)
        vm.state.first { !it.loading }
        vm.save()
        assertThat(vm.state.value.distanceError).isEqualTo(DistanceError.Missing)
        vm.onStartOdometer("44466")
        vm.onEndOdometer("44320")
        vm.save()
        assertThat(vm.state.value.distanceError).isEqualTo(DistanceError.EndNotAfterStart)
        assertThat(trips.trips).isEmpty()
    }

    @Test
    fun editingNotesKeepsTheSavedEstimate() = runTest {
        val trips = FakeTripRepository(listOf(mysuru))
        val vm = editor(trips, "mysuru")
        vm.state.first { !it.loading }
        vm.onNotes("Weekend with family")
        vm.save()
        vm.saved.first()
        assertThat(trips.trips.single().estimatedFuelCost).isEqualTo(Money(89_600))
        assertThat(trips.trips.single().notes).isEqualTo("Weekend with family")
    }

    @Test
    fun listGroupsByWeekAndAddsLinkedTolls() = runTest {
        val commute = mysuru.copy(
            id = "c1",
            startUtcMs = SampleEntries.at(2026, 10, 15, hour = 18),
            endUtcMs = null,
            distanceKm = 23.1,
            purpose = TripPurpose.Commute,
            fromPlace = "Whitefield",
            toPlace = "HSR Layout",
            estimatedFuelCost = Money(15_300),
        )
        val repo = FakeTripRepository(listOf(mysuru, commute))
        val vm = TripsViewModel(
            repo,
            vehicles,
            FakeEntryRepository(expenses = listOf(toll)),
            userData,
            recorder(repo),
            zone,
            SavedStateHandle(),
        )
        val weeks = vm.state.first { !it.loading }.weeks
        assertThat(weeks.map { it.start }).containsExactly(LocalDate.of(2026, 10, 12)).inOrder()
        assertThat(weeks.single().km).isWithin(1e-9).of(169.1)
        // ₹896 + ₹230 toll + ₹153
        assertThat(weeks.single().cost).isEqualTo(Money(127_900))
    }

    @Test
    fun sharingAsksForTheUpiIdOnceThenSendsTheLink() = runTest {
        val vm = detail(FakeTripRepository(listOf(mysuru)))
        val loaded = vm.state.first { !it.loading }
        assertThat(loaded.split?.total).isEqualTo(Money(112_600))
        vm.onPeople(3)
        assertThat(vm.state.first { it.split?.people == 3 }.split?.share).isEqualTo(Money(37_500))

        vm.share("Bengaluru → Mysuru")
        assertThat(vm.events.first()).isEqualTo(TripDetailEvent.NeedUpi)
        assertThat(vm.saveUpiAndShare("not-an-id", "Asha", "Bengaluru → Mysuru")).isFalse()
        assertThat(vm.state.first { it.upiError }.upiError).isTrue()

        assertThat(vm.saveUpiAndShare("asha.k@okaxis", "Asha K", "Bengaluru → Mysuru")).isTrue()
        val share = vm.events.first() as TripDetailEvent.Share
        assertThat(share.link).startsWith("upi://pay?pa=asha.k%40okaxis&pn=Asha%20K&am=375.00&cu=INR")
        assertThat(userData.userData.value.upiVpa).isEqualTo("asha.k@okaxis")
    }

    @Test
    fun aloneThereIsNoLink() = runTest {
        val vm = detail(FakeTripRepository(listOf(mysuru)))
        vm.state.first { !it.loading }
        vm.onPeople(1)
        vm.state.first { it.split?.people == 1 }
        vm.share("Bengaluru → Mysuru")
        assertThat((vm.events.first() as TripDetailEvent.Share).link).isNull()
    }

    @Test
    fun gpsTripStartsRecordsAndStopsIntoTheList() = runTest {
        val repo = FakeTripRepository()
        val recorder = recorder(repo)
        val list = TripsViewModel(
            repo,
            vehicles,
            FakeEntryRepository(),
            userData,
            recorder,
            zone,
            SavedStateHandle(),
        )
        list.startTrip()
        assertThat(list.events.first()).isEqualTo(TripsEvent.OpenLive)
        runCurrent()
        assertThat(list.state.first { it.recording != null }.recording?.km).isEqualTo(0.0)

        val refuels = FakeEntryRepository(refuels = SampleEntries.safariRefuels)
        val live = LiveTripViewModel(recorder, vehicles, refuels, estimate)
        source.emit((0..10).map { TrackPoint(it * 5_000L, 12.900 + it * 0.001, 77.6, 8f, 20f, 0) })
        runCurrent()
        val recording = live.state.first { (it.recording?.summary?.distanceKm ?: 0.0) > 1.0 }
        assertThat(recording.vehicleName).isEqualTo("Safari")
        assertThat(recording.estimate).isNotNull()

        live.stop()
        val finished = live.events.first() as LiveTripEvent.Finished
        val trip = repo.trips.single()
        assertThat(finished.tripId).isEqualTo(trip.id)
        assertThat(trip.status).isEqualTo(TripStatus.Done)
        assertThat(trip.distanceKm).isWithin(0.01).of(1.11)
        val after = list.state.first { it.recording == null }
        assertThat(after.weeks.single().rows.single().trip.id).isEqualTo(trip.id)
        assertThat(after.interrupted).isEmpty()
    }

    @Test
    fun aTripCutShortOffersFinish() = runTest {
        val cut = mysuru.copy(
            id = "cut",
            source = TripSource.Gps,
            status = TripStatus.Recording,
            endUtcMs = null,
            estimatedFuelCost = null,
        )
        val repo = FakeTripRepository(listOf(cut))
        repo.appendPoints("cut", (0..10).map { TrackPoint(it * 5_000L, 12.900 + it * 0.001, 77.6, 8f, null, 0) })
        val list =
            TripsViewModel(repo, vehicles, FakeEntryRepository(), userData, recorder(repo), zone, SavedStateHandle())
        val state = list.state.first { !it.loading }
        assertThat(state.interrupted.map { it.id }).containsExactly("cut")
        // Unfinished trips stay out of the weekly list until finished.
        assertThat(state.weeks).isEmpty()
        list.finishInterrupted("cut")
        assertThat(list.events.first()).isEqualTo(TripsEvent.OpenTrip("cut"))
        assertThat(repo.trips.single().status).isEqualTo(TripStatus.Done)
    }

    @Test fun liveLight() = capture("trip_live_light", false) {
        LiveTripContent(liveState(), clock.millis(), TripsLinks(), LiveTripActions())
    }

    @Test fun liveDark() = capture("trip_live_dark", true) {
        LiveTripContent(liveState(), clock.millis(), TripsLinks(), LiveTripActions())
    }

    @Test fun listRecordingLight() = capture("trips_list_recording_light", false) {
        TripsContent(
            listState().copy(
                recording = RecordingBanner("live", 12.4, paused = false),
                interrupted = listOf(mysuru.copy(id = "cut", status = TripStatus.Recording)),
            ),
            TripsLinks(),
            zone = zone,
        )
    }

    @Test fun detailGpsLight() = capture("trip_detail_gps_light", false) {
        TripDetailContent(
            detailState().copy(
                trip = mysuru.copy(
                    source = TripSource.Gps,
                    recordedSeconds = 9_840,
                    idleSeconds = 720,
                    maxSpeedKmh = 96.0,
                ),
                route = liveState().recording!!.route,
            ),
            TripsLinks(),
            TripDetailActions(),
        )
    }

    @Test fun listLight() = capture("trips_list_light", false) { TripsContent(listState(), TripsLinks(), zone = zone) }

    @Test fun listDark() = capture("trips_list_dark", true) { TripsContent(listState(), TripsLinks(), zone = zone) }

    @Test fun detailLight() = capture("trip_detail_light", false) {
        TripDetailContent(detailState(), TripsLinks(), TripDetailActions())
    }

    @Test fun detailDark() = capture("trip_detail_dark", true) {
        TripDetailContent(detailState(), TripsLinks(), TripDetailActions())
    }

    @Test fun editorLight() = capture("trip_editor_light", false) {
        TripEditorContent(
            TripFormState(
                loading = false,
                vehicles = listOf(SampleVehicles.safari),
                vehicle = SampleVehicles.safari,
                startUtcMs = clock.millis(),
                startOdometer = "44320",
                endOdometer = "44466",
                purpose = TripPurpose.Business,
                fromPlace = "Bengaluru",
                toPlace = "Mysuru",
                odometerDistanceKm = 146.0,
                estimate = TripCostEstimate(
                    Money(84_439),
                    TripCostBasis.Efficiency(EnergyType.DIESEL, 9.46, 15.44, Money(8_930), isManual = false),
                ),
            ),
            TripEditorActions(),
            onClose = {},
            nowUtcMs = clock.millis(),
        )
    }

    private fun listState() = TripsUiState(
        loading = false,
        hasVehicles = true,
        weeks = listOf(
            TripWeekUi(
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 18),
                169.1,
                Money(127_900),
                listOf(
                    TripRow(
                        mysuru.copy(
                            id = "c1",
                            startUtcMs = SampleEntries.at(2026, 10, 15, 18),
                            endUtcMs = SampleEntries.at(2026, 10, 15, 19),
                            distanceKm = 23.1,
                            purpose = TripPurpose.Commute,
                            fromPlace = "Whitefield",
                            toPlace = "HSR Layout",
                        ),
                        Money(15_300),
                        null,
                    ),
                    TripRow(mysuru, Money(112_600), null),
                ),
            ),
        ),
    )

    private fun detailState() = TripDetailUiState(
        loading = false,
        trip = mysuru,
        vehicle = SampleVehicles.safari,
        linked = listOf(LinkedCost(toll, FakeCategoryRepository.SYSTEM.first { it.id == toll.categoryId })),
        fuelBasis = TripCostBasis.Efficiency(EnergyType.DIESEL, 10.0, 14.6, Money(8_960), isManual = false),
        split = TripSplit(3, Money(112_600), Money(37_500)),
    )

    private fun liveState(): LiveTripUiState {
        // A bend through Koramangala, with a pause in the middle.
        val route = listOf(
            TrackPoint(0, 12.9352, 77.6245, 8f, null, 0),
            TrackPoint(60_000, 12.9300, 77.6290, 8f, null, 0),
            TrackPoint(120_000, 12.9280, 77.6380, 8f, null, 0),
            TrackPoint(400_000, 12.9220, 77.6420, 8f, null, 1),
            TrackPoint(460_000, 12.9150, 77.6440, 8f, null, 1),
        )
        return LiveTripUiState(
            loading = false,
            recording = RecordingState.Active(
                tripId = "live",
                vehicleId = "safari",
                startUtcMs = clock.millis() - 2_292_000,
                paused = false,
                summary = TrackSummary(23.4, 2_292, 300, 62.0),
                route = route,
                activeMsBefore = 0,
                activeSinceUtcMs = clock.millis() - 2_292_000,
            ),
            vehicleName = "Safari",
            estimate = Money(15_900),
        )
    }

    private fun TestScope.recorder(repo: FakeTripRepository) = TripRecorder(
        repo,
        vehicles,
        FakeEntryRepository(refuels = SampleEntries.safariRefuels),
        estimate,
        source,
        object : RecordingHost {
            override fun startForegroundRecording() = Unit
        },
        clock,
        zone,
        backgroundScope,
    )

    private val source = object : TripLocationSource {
        private val batches = MutableSharedFlow<List<TrackPoint>>()

        override val hasPermission = true

        override fun fixes(lifetimeMs: Long): Flow<List<TrackPoint>> = batches

        override fun flush() = Unit

        suspend fun emit(batch: List<TrackPoint>) = batches.emit(batch)
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        // Robolectric can't run the Maps SDK: maps draw their plain fallback.
        compose.setContent {
            CompositionLocalProvider(LocalMapsEnabled provides false) { FuelwiseTheme(darkTheme = dark) { content() } }
        }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}

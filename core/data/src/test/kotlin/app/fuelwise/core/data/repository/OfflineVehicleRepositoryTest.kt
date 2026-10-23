package app.fuelwise.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.core.domain.vehicle.VehicleDraft
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineVehicleRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-16T04:30:00Z"), ZoneOffset.UTC)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FuelwiseDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val repository = OfflineVehicleRepository(
        dao = db.vehicleDao(),
        ids = UuidV7Generator(clock),
        clock = clock,
        deviceIds = { "device-a" },
        zoneId = ZoneId.of("Asia/Kolkata"),
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun addCreatesTanksFromTheFuelSetupWithPrimaryFirst() = runTest {
        val id = repository.add(
            VehicleDraft(
                name = " Ertiga ",
                type = VehicleType.Car,
                fuelSetup = FuelSetup.CngPetrol,
                odometerKm = 31_045.0,
                tankCapacities = listOf(12.0, 45.0),
                registrationNumber = "mh02cd5678",
            ),
        )
        val vehicle = repository.observeVehicle(id).first()!!

        assertThat(vehicle.name).isEqualTo("Ertiga")
        assertThat(vehicle.registrationNumber).isEqualTo("MH 02 CD 5678")
        assertThat(vehicle.isBiFuel).isTrue()
        assertThat(vehicle.tanks.map { it.energyType }).containsExactly(EnergyType.CNG, EnergyType.PETROL).inOrder()
        assertThat(vehicle.tanks.map { it.capacity }).containsExactly(12.0, 45.0).inOrder()
        assertThat(vehicle.primaryTank!!.energyType).isEqualTo(EnergyType.CNG)
        assertThat(vehicle.startOdometerKm).isEqualTo(31_045.0)
        assertThat(vehicle.startOdometerAtUtcMs).isEqualTo(clock.millis())
    }

    @Test
    fun detailsHybridAndDefaultGradeAreKept() = runTest {
        val id = repository.add(
            VehicleDraft(
                name = "City",
                fuelSetup = FuelSetup.Petrol,
                odometerKm = 8_200.0,
                make = " Honda ",
                model = "City",
                variant = "e:HEV ZX",
                year = 2025,
                purchaseDate = LocalDate.of(2025, 2, 14),
                purchaseOdometerKm = 6.0,
                purchasePrice = Money(205_000_000),
                isHybrid = true,
                defaultGrade = FuelGrade.E20,
            ),
        )
        val city = repository.observeVehicle(id).first()!!
        assertThat(city.make).isEqualTo("Honda")
        assertThat(city.variant).isEqualTo("e:HEV ZX")
        assertThat(city.year).isEqualTo(2025)
        assertThat(city.purchaseDate).isEqualTo(LocalDate.of(2025, 2, 14))
        assertThat(city.purchasePrice).isEqualTo(Money(205_000_000))
        assertThat(city.isHybrid).isTrue()
        assertThat(city.primaryTank!!.defaultGrade).isEqualTo(FuelGrade.E20)
        // Edits keep the grade.
        repository.update(city.copy(tanks = city.tanks.map { it.copy(defaultGrade = FuelGrade.Premium) }))
        assertThat(repository.observeVehicle(id).first()!!.primaryTank!!.defaultGrade).isEqualTo(FuelGrade.Premium)
    }

    @Test
    fun newVehiclesGoToTheEndOfTheGarage() = runTest {
        val first = repository.add(draft("Safari"))
        val second = repository.add(draft("Activa"))
        assertThat(repository.observeVehicles().first().map { it.id }).containsExactly(first, second).inOrder()
    }

    @Test
    fun archiveHidesFromTheSwitcherButKeepsTheVehicle() = runTest {
        val id = repository.add(draft("Old Alto"))
        repository.setArchived(id, archived = true)

        assertThat(repository.observeVehicles().first()).isEmpty()
        assertThat(repository.observeVehicles(includeArchived = true).first().single().isArchived).isTrue()
    }

    @Test
    fun deleteIsUndoable() = runTest {
        val id = repository.add(draft("Nexon EV", FuelSetup.Electric))
        val token = repository.delete(id)
        assertThat(repository.observeVehicle(id).first()).isNull()

        repository.undoDelete(token)
        val restored = repository.observeVehicle(id).first()!!
        assertThat(restored.tanks.single().energyType).isEqualTo(EnergyType.ELECTRIC)
    }

    @Test
    fun updateKeepsTheStartOdometerAndEditsFields() = runTest {
        val id = repository.add(draft("Safari"))
        val vehicle = repository.observeVehicle(id).first()!!
        repository.update(vehicle.copy(name = "Safari Dicor", claimedKmPerUnit = 19.1))

        val updated = repository.observeVehicle(id).first()!!
        assertThat(updated.name).isEqualTo("Safari Dicor")
        assertThat(updated.claimedKmPerUnit).isEqualTo(19.1)
        assertThat(updated.startOdometerKm).isEqualTo(42_318.0)
    }

    private fun draft(name: String, setup: FuelSetup = FuelSetup.Diesel) =
        VehicleDraft(name = name, fuelSetup = setup, odometerKm = 42_318.0)
}

package app.fuelwise.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.database.model.SyncMeta
import app.fuelwise.core.database.model.TankEntity
import app.fuelwise.core.database.model.VehicleEntity
import app.fuelwise.core.model.SystemCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VehicleDaoTest {
    private lateinit var db: FuelwiseDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuelwiseDatabase::class.java)
            .addCallback(FuelwiseDatabase.SEED_CALLBACK)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedsAllSystemCategoriesOnCreate() = runTest {
        val categories = db.categoryDao().observeCategories().first()
        assertThat(categories.map { it.id }).containsExactlyElementsIn(SystemCategory.entries.map { it.id }).inOrder()
        assertThat(categories.single { it.id == SystemCategory.LoanEmi.id }.includeInCostPerKm).isFalse()
        assertThat(categories.single { it.id == SystemCategory.Service.id }.includeInCostPerKm).isTrue()
    }

    @Test
    fun archivedVehiclesAreHiddenUnlessAsked() = runTest {
        val dao = db.vehicleDao()
        dao.upsertVehicleWithTanks(vehicle("v1", "Safari"), listOf(tank("t1", "v1")))
        dao.upsertVehicleWithTanks(vehicle("v2", "Old Alto", archived = true), emptyList())

        assertThat(
            dao.observeVehicles(includeArchived = false).first().map {
                it.vehicle.name
            },
        ).containsExactly("Safari")
        assertThat(dao.observeVehicles(includeArchived = true).first()).hasSize(2)
    }

    @Test
    fun softDeleteTombstonesVehicleAndTanksAndRestoreBringsThemBack() = runTest {
        val dao = db.vehicleDao()
        dao.upsertVehicleWithTanks(vehicle("v1", "Ertiga"), listOf(tank("cng", "v1"), tank("petrol", "v1")))

        dao.softDelete("v1", at = 5_000, deviceId = "d1")
        assertThat(dao.observeVehicles(includeArchived = true).first()).isEmpty()
        assertThat(dao.observeVehicle("v1").first()).isNull()

        dao.restore("v1", deletedAt = 5_000, at = 6_000, deviceId = "d1")
        val restored = dao.observeVehicle("v1").first()!!
        assertThat(restored.tanks.map { it.sync.deletedAt }).containsExactly(null, null)
        assertThat(restored.vehicle.sync.updatedAt).isEqualTo(6_000)
    }

    @Test
    fun restoreDoesNotRevivePreviouslyDeletedTanks() = runTest {
        val dao = db.vehicleDao()
        val removedEarlier = tank("old", "v1").copy(sync = SyncMeta(1, 2, "d1", deletedAt = 2))
        dao.upsertVehicleWithTanks(vehicle("v1", "Safari"), listOf(tank("t1", "v1"), removedEarlier))

        dao.softDelete("v1", at = 9_000, deviceId = "d1")
        dao.restore("v1", deletedAt = 9_000, at = 9_500, deviceId = "d1")

        val tanks = dao.observeVehicle("v1").first()!!.tanks.associate { it.id to it.sync.deletedAt }
        assertThat(tanks).containsExactly("t1", null, "old", 2L)
    }

    private fun vehicle(id: String, name: String, archived: Boolean = false) = VehicleEntity(
        id = id, name = name, type = "Car", make = null, model = null, variant = null, year = null,
        registrationNumber = null, emissionNorm = null, purchaseEpochDay = null, purchaseOdometerKm = null,
        purchasePricePaise = null, claimedKmPerUnit = null, isHybrid = false, customKmPerUnit = null,
        customPricePerUnitPaise = null, customRangeKm = null, archived = archived, sortOrder = 0,
        startOdometerKm = 42_318.0, startOdometerAt = 1, startOdometerZoneId = "Asia/Kolkata",
        sync = SyncMeta(1, 1, "d1"),
    )

    private fun tank(id: String, vehicleId: String) = TankEntity(
        id = id,
        vehicleId = vehicleId,
        energyType = "DIESEL",
        capacity = 50.0,
        isPrimary = true,
        defaultGrade = null,
        sync = SyncMeta(1, 1, "d1"),
    )
}

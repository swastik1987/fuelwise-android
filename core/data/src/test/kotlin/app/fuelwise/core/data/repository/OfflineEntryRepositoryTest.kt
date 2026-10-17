package app.fuelwise.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.database.FuelwiseDatabase
import app.fuelwise.core.domain.id.UuidV7Generator
import app.fuelwise.core.domain.vehicle.VehicleDraft
import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
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
class OfflineEntryRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-16T04:30:00Z"), ZoneOffset.UTC)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FuelwiseDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val ids = UuidV7Generator(clock)
    private val entries = OfflineEntryRepository(db.entryDao(), ids, clock) { "device-a" }
    private val vehicles =
        OfflineVehicleRepository(db.vehicleDao(), ids, clock, { "device-a" }, ZoneId.of("Asia/Kolkata"))

    @After
    fun tearDown() = db.close()

    @Test
    fun saveAssignsAnIdAndKeepsPaidEqualToTotalMinusDiscount() = runTest {
        val id = entries.saveRefuel(refuel("v1", discount = 5_000))
        val saved = entries.observeRefuel(id).first()!!
        assertThat(saved.paid).isEqualTo(Money(328_461))
        assertThat(entries.observeRefuels("v1").first()).hasSize(1)
    }

    @Test
    fun editingKeepsTheId() = runTest {
        val id = entries.saveRefuel(refuel("v1"))
        entries.saveRefuel(entries.observeRefuel(id).first()!!.copy(notes = "Highway run"))
        assertThat(entries.observeRefuels("v1").first().single().notes).isEqualTo("Highway run")
    }

    @Test
    fun deleteAndUndo() = runTest {
        val id = entries.saveExpense(expense("v1", plaza = "Attibele"))
        val token = entries.deleteExpense(id)
        assertThat(entries.observeExpenses("v1").first()).isEmpty()
        entries.undoDelete(token)
        assertThat(entries.observeExpenses("v1").first()).hasSize(1)
        assertThat(entries.observeRecentPlazas().first()).containsExactly("Attibele")
    }

    @Test
    fun deletingAVehicleCascadesToItsEntriesAndUndoRestoresThem() = runTest {
        val vehicleId = vehicles.add(VehicleDraft(name = "Safari", fuelSetup = FuelSetup.Diesel, odometerKm = 42_000.0))
        entries.saveRefuel(refuel(vehicleId))
        entries.saveExpense(expense(vehicleId))

        val token = vehicles.delete(vehicleId)
        assertThat(entries.observeAllRefuels().first()).isEmpty()
        assertThat(entries.observeAllExpenses().first()).isEmpty()

        vehicles.undoDelete(token)
        assertThat(entries.observeRefuels(vehicleId).first()).hasSize(1)
        assertThat(entries.observeExpenses(vehicleId).first()).hasSize(1)
    }

    private fun refuel(vehicleId: String, discount: Long = 0) = Refuel(
        id = "",
        vehicleId = vehicleId,
        tankId = "t1",
        dateTimeUtcMs = clock.millis(),
        zoneId = "Asia/Kolkata",
        odometerKm = 42_318.0,
        quantity = 32.4,
        unitPrice = Money(10_292),
        total = Money(333_461),
        discount = Money(discount),
        isFull = true,
    )

    private fun expense(vehicleId: String, plaza: String? = null) = Expense(
        id = "",
        vehicleId = vehicleId,
        categoryId = if (plaza != null) SystemCategory.TollFastag.id else SystemCategory.Service.id,
        dateTimeUtcMs = clock.millis(),
        zoneId = "Asia/Kolkata",
        amount = Money(16_500),
        tollPlaza = plaza,
    )
}

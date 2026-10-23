package app.fuelwise.core.domain.vehicle

import app.fuelwise.core.domain.vehicle.VehicleDraftError.CapacityNotPositive
import app.fuelwise.core.domain.vehicle.VehicleDraftError.ClaimedNotPositive
import app.fuelwise.core.domain.vehicle.VehicleDraftError.NameRequired
import app.fuelwise.core.domain.vehicle.VehicleDraftError.NameTooLong
import app.fuelwise.core.domain.vehicle.VehicleDraftError.OdometerNegative
import app.fuelwise.core.domain.vehicle.VehicleDraftError.OdometerRequired
import app.fuelwise.core.domain.vehicle.VehicleDraftError.RegistrationInvalid
import app.fuelwise.core.model.CustomParameters
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class ValidateVehicleDraftTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-23T04:30:00Z"), ZoneOffset.UTC)
    private val validate = ValidateVehicleDraft(clock)

    @Test
    fun `name, type, fuel and odometer are enough`() {
        val draft =
            VehicleDraft(
                name = "Activa",
                type = VehicleType.Scooter,
                fuelSetup = FuelSetup.Petrol,
                odometerKm = 9_870.0,
            )
        assertThat(validate(draft)).isEmpty()
    }

    @Test
    fun `missing required fields`() {
        assertThat(validate(VehicleDraft(name = "  "))).containsExactly(NameRequired, OdometerRequired)
    }

    @Test
    fun `optional fields are checked only when filled`() {
        val draft = VehicleDraft(
            name = "Ertiga".repeat(10),
            fuelSetup = FuelSetup.CngPetrol,
            odometerKm = -1.0,
            tankCapacities = listOf(12.0, 0.0),
            registrationNumber = "MH 02 CD",
            claimedKmPerUnit = 0.0,
        )
        assertThat(validate(draft))
            .containsExactly(
                NameTooLong,
                OdometerNegative,
                CapacityNotPositive,
                RegistrationInvalid,
                ClaimedNotPositive,
            )
    }

    @Test
    fun `zero odometer is allowed for a new vehicle`() {
        assertThat(
            validate(VehicleDraft(name = "Nexon EV", fuelSetup = FuelSetup.Electric, odometerKm = 0.0)),
        ).isEmpty()
    }

    @Test
    fun `purchase details must add up`() {
        val ok = VehicleDraft(
            name = "Safari",
            odometerKm = 42_000.0,
            make = "Tata",
            year = 2027,
            purchaseDate = LocalDate.of(2024, 3, 9),
            purchaseOdometerKm = 12.0,
            purchasePrice = Money(215_000_000),
        )
        assertThat(validate(ok)).isEmpty()
        val wrong = ok.copy(
            year = 2028,
            purchaseDate = LocalDate.of(2026, 10, 24),
            purchaseOdometerKm = 42_500.0,
            purchasePrice = Money(0),
        )
        assertThat(validate(wrong)).containsExactly(
            VehicleDraftError.YearInvalid,
            VehicleDraftError.PurchaseInFuture,
            VehicleDraftError.PurchaseOdometerInvalid,
            VehicleDraftError.PurchasePriceNotPositive,
        )
        assertThat(validate(ok.copy(year = 1949))).containsExactly(VehicleDraftError.YearInvalid)
    }

    @Test
    fun `custom parameters must be positive when set`() {
        val draft = VehicleDraft(name = "Safari", odometerKm = 42_000.0)
        assertThat(validate(draft.copy(custom = CustomParameters(kmPerUnit = 14.5, rangeKm = 650.0)))).isEmpty()
        assertThat(validate(draft.copy(custom = CustomParameters(pricePerUnit = Money(0)))))
            .containsExactly(VehicleDraftError.CustomNotPositive)
    }
}

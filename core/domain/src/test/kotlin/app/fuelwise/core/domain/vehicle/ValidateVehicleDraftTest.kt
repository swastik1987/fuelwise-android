package app.fuelwise.core.domain.vehicle

import app.fuelwise.core.domain.vehicle.VehicleDraftError.CapacityNotPositive
import app.fuelwise.core.domain.vehicle.VehicleDraftError.ClaimedNotPositive
import app.fuelwise.core.domain.vehicle.VehicleDraftError.NameRequired
import app.fuelwise.core.domain.vehicle.VehicleDraftError.NameTooLong
import app.fuelwise.core.domain.vehicle.VehicleDraftError.OdometerNegative
import app.fuelwise.core.domain.vehicle.VehicleDraftError.OdometerRequired
import app.fuelwise.core.domain.vehicle.VehicleDraftError.RegistrationInvalid
import app.fuelwise.core.model.FuelSetup
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ValidateVehicleDraftTest {
    private val validate = ValidateVehicleDraft()

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
}

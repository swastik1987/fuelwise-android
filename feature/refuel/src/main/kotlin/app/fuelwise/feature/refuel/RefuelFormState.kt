package app.fuelwise.feature.refuel

import app.fuelwise.core.domain.entry.CheckFillUp
import app.fuelwise.core.domain.entry.FillReward
import app.fuelwise.core.domain.entry.PriceTriangle
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.PriceSource
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle

/** Numeric fields driven by the in-sheet keypad. Text fields (station, notes, operator) use the system keyboard. */
enum class NumericField { Odometer, Quantity, UnitPrice, Total, Discount, Duration }

/** Problems the user must fix before saving. */
enum class RequiredField { Odometer, Quantity, Total }

/**
 * Everything the Add fuel / Add charge sheet shows (design.md screens 6 and 8). Numbers are kept as the exact text
 * typed; the computed triangle field is re-derived on every change.
 */
data class RefuelFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val vehicle: Vehicle? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val tankIndex: Int = 0,
    val dateTimeUtcMs: Long = 0,
    val odometer: String = "",
    val predictedOdometer: Double? = null,
    val quantity: String = "",
    val unitPrice: String = "",
    val total: String = "",
    val recentEdits: List<PriceTriangle.Field> = emptyList(),
    val priceSource: PriceSource = PriceSource.Typed,
    /** The price came from the home charging tariff in Settings (PRD F-02.5), so the row says so. */
    val homeTariffPrice: Boolean = false,
    val focus: NumericField? = NumericField.Odometer,
    val isFull: Boolean = true,
    val moreExpanded: Boolean = false,
    val grade: FuelGrade? = null,
    val payment: PaymentMethod? = null,
    val discount: String = "",
    val station: String = "",
    val notes: String = "",
    val missedPrevious: Boolean = false,
    val socStart: Float = DEFAULT_SOC_START,
    val socEnd: Float = DEFAULT_SOC_END,
    val charger: ChargerType? = null,
    val operator: String = "",
    val durationMin: String = "",
    val missing: Set<RequiredField> = emptySet(),
    val problems: List<CheckFillUp.Problem> = emptyList(),
    val warnings: List<CheckFillUp.Warning> = emptyList(),
    val saving: Boolean = false,
    val reward: FillReward? = null,
    /** A scan photo waiting to be attached on save (PRD F-15); null when none or removed. */
    val scanPhotoToken: String? = null,
) {
    val tank: Tank? get() = vehicle?.tanks?.getOrNull(tankIndex)
    val isCharge: Boolean get() = tank?.energyType == EnergyType.ELECTRIC
    val computedField: PriceTriangle.Field get() = PriceTriangle.computedField(recentEdits)

    companion object {
        const val DEFAULT_SOC_START = 20f
        const val DEFAULT_SOC_END = 80f
    }
}

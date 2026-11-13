package app.fuelwise.feature.refuel

import app.fuelwise.core.designsystem.component.FwPhoto
import app.fuelwise.core.domain.entry.CheckFillUp
import app.fuelwise.core.domain.entry.FillReward
import app.fuelwise.core.domain.entry.PriceTriangle
import app.fuelwise.core.domain.prices.FairPrice
import app.fuelwise.core.domain.station.StationOption
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.PriceSource
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.format.NumberInput

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
    /** The vehicle's display units (PRD F-18); entry fields stay in km and litres. */
    val units: DisplayUnits = DisplayUnits.INDIA,
    val tankIndex: Int = 0,
    val dateTimeUtcMs: Long = 0,
    val odometer: String = "",
    /**
     * "Trip meter since last fill" (PRD F-02.4): the trip meter as typed, or null in odometer mode. [odometer] holds
     * the converted reading either way, so checks and save don't care which mode was used.
     */
    val tripMeter: String? = null,
    /** The vehicle's previous fill-up reading; the trip meter mode is offered only when there is one. */
    val previousFillOdometer: Double? = null,
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
    /** The discount is typed as a percentage of the total rather than in rupees (PRD F-02.2). Stored as rupees. */
    val discountPercent: Boolean = false,
    val station: String = "",
    /** Station auto-detect (PRD F-21.1) for a new entry; [StationDetect.Off] for edits and back-dated entries. */
    val stationDetect: StationDetect = StationDetect.Off,
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
    /** "Did I pay fair?" for the saved fill (PRD F-09.3); null without a home-city price for that day. */
    val fairPrice: FairPrice? = null,
    /** A scan photo waiting to be attached on save (PRD F-15); null when none or removed. */
    val scanPhotoToken: String? = null,
    /** Receipt or pump photos (PRD F-02.1), saved with the entry. */
    val photos: List<FwPhoto> = emptyList(),
    val canAddPhoto: Boolean = true,
    val photoFailed: Boolean = false,
) {
    val tank: Tank? get() = vehicle?.tanks?.getOrNull(tankIndex)
    val isCharge: Boolean get() = tank?.energyType == EnergyType.ELECTRIC
    val computedField: PriceTriangle.Field get() = PriceTriangle.computedField(recentEdits)

    companion object {
        const val DEFAULT_SOC_START = 20f
        const val DEFAULT_SOC_END = 80f
    }
}

/** The discount in rupees: as typed, or worked out from a percentage of [total] (PRD F-02.2). */
internal fun RefuelFormState.discountMoney(total: Money?): Money? = if (discountPercent) {
    PriceTriangle.discountOf(total, NumberInput.parseDecimal(discount))
} else {
    MoneyInput.parse(discount)
}

/** Station auto-detect on the sheet (PRD F-21.1). Detected stations are suggestions; nothing is saved until Save. */
sealed interface StationDetect {
    /** Not offered: an edit, a fill-up prompt (it brings its station), a back-dated entry, or no location fix. */
    data object Off : StationDetect

    /** Location isn't allowed yet: a "Detect station" chip asks for it (while the app is in use only). */
    data object NeedsPermission : StationDetect

    data object Locating : StationDetect

    /** Up to three nearby stations, best first. [selected] is null for "Other", where the user types a name. */
    data class Found(val options: List<StationOption>, val selected: Int?) : StationDetect {
        val selectedOption: StationOption? get() = selected?.let(options::getOrNull)
    }

    /** A fix but no station near it: the user types the name, and it's remembered for this spot. */
    data object NotFound : StationDetect
}

/** The station field shows in the detect section (not under More) once a location was found. */
internal val StationDetect.ownsStationField: Boolean
    get() = this == StationDetect.NotFound || (this is StationDetect.Found && selected == null)

internal val StationDetect.hasLocation: Boolean get() = this is StationDetect.Found || this == StationDetect.NotFound

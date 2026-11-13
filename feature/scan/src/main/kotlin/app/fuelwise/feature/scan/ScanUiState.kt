package app.fuelwise.feature.scan

import app.fuelwise.core.domain.scan.OcrBox
import app.fuelwise.core.domain.scan.OcrLine
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.ScanPrefill

/**
 * One analysed camera frame. [width]/[height] are the upright image size the [lines] boxes refer to. [jpeg] encodes
 * this frame's image; it is only called when the user keeps the photo.
 */
class OcrFrame(val lines: List<OcrLine>, val width: Int, val height: Int, val jpeg: () -> ByteArray?)

enum class CameraAccess { Unknown, Granted, Denied }

/** A value the scan can fill. The review sheet lists the ones read, each with its own confirm tick. */
enum class ScanField { Total, Quantity, Rate, Odometer, Date, Station }

/** A number floating over the camera view where it was read (design.md screen 9). */
data class LiveLabel(val field: ScanField, val box: OcrBox)

/**
 * The confirm step (PRD F-15: never save without the user). [validated] readings arrive with every field ticked;
 * otherwise the user ticks each one they trust. [values] holds what was read, [confirmed] what will be used.
 */
data class ScanReview(
    val values: ScanPrefill,
    val fields: List<ScanField>,
    val confirmed: Set<ScanField>,
    val validated: Boolean,
    val hasPhoto: Boolean,
)

data class ScanUiState(
    val mode: ScanMode = ScanMode.PumpMeter,
    val camera: CameraAccess = CameraAccess.Unknown,
    val unit: QuantityUnit = QuantityUnit.LITRE,
    /** The latest reading, for the live labels and "Read now". */
    val live: ScanPrefill = ScanPrefill(),
    val labels: List<LiveLabel> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    /** All fields read and consistent: the checkmark on the guide. */
    val allValid: Boolean = false,
    /** Nothing usable for a while: "Couldn't read clearly. Try less glare, or type it in." */
    val struggling: Boolean = false,
    val review: ScanReview? = null,
    val attachPhoto: Boolean = true,
    /** A "Snap" photo is being taken and read. */
    val snapping: Boolean = false,
) {
    val hasReading: Boolean get() = live.fields().isNotEmpty()
}

/** The fields [this] carries, in the order the review sheet shows them. */
internal fun ScanPrefill.fields(): List<ScanField> = buildList {
    if (total != null) add(ScanField.Total)
    if (quantity != null) add(ScanField.Quantity)
    if (unitPrice != null) add(ScanField.Rate)
    if (odometerKm != null) add(ScanField.Odometer)
    if (dateTimeUtcMs != null) add(ScanField.Date)
    if (stationName != null) add(ScanField.Station)
}

/** Only the ticked fields. */
internal fun ScanPrefill.keep(fields: Set<ScanField>) = ScanPrefill(
    total = total.takeIf { ScanField.Total in fields },
    quantity = quantity.takeIf { ScanField.Quantity in fields },
    unitPrice = unitPrice.takeIf { ScanField.Rate in fields },
    odometerKm = odometerKm.takeIf { ScanField.Odometer in fields },
    dateTimeUtcMs = dateTimeUtcMs.takeIf { ScanField.Date in fields },
    stationName = stationName.takeIf { ScanField.Station in fields },
)

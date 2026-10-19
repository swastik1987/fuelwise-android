package app.fuelwise.core.domain.scan

import app.fuelwise.core.model.Money

/**
 * Every knob the scan parsers use, in one place so they can be tuned against the real photo set (PRD F-15, D9).
 *
 * TODO(F-15 AC): the defaults are tuned on text fixtures only. Collect the 50 real pump-display photos, run them
 *  through ML Kit, add the recognised text to the fixtures in `ScanFixtures` and re-tune until ≥85% auto-fill all
 *  three fields correctly. See docs/scan-tuning.md.
 */
data class ScanTuning(
    /** The price triangle check: |quantity × rate − total| ≤ this (PRD F-15: ₹1). */
    val triangleTolerance: Money = Money(TOLERANCE_PAISE),
    /** Plausible ₹ per L / kg for petrol, diesel, CNG and autogas LPG in India. */
    val minRate: Money = Money.ofRupees(MIN_RATE_RUPEES),
    val maxRate: Money = Money.ofRupees(MAX_RATE_RUPEES),
    /** L or kg in one fill. Trucks can take a few hundred litres. */
    val minQuantity: Double = 0.1,
    val maxQuantity: Double = 500.0,
    val minTotal: Money = Money.ofRupees(MIN_TOTAL_RUPEES),
    val maxTotal: Money = Money.ofRupees(MAX_TOTAL_RUPEES),
    /** A label on the same row as the number ("RATE 102.92"). */
    val sameRowLabelWeight: Double = 3.0,
    /** A label on the row above or below (pump displays print labels under or over the digits). */
    val nearbyLabelWeight: Double = 1.5,
    /** How strongly a label for another field counts against a reading, as a fraction of its weight. */
    val contradictionFactor: Double = 0.8,
    /** A triple that passes the price triangle check. Dominates label evidence on purpose. */
    val triangleFitBonus: Double = 12.0,
    /** Pump displays list amount, volume, rate from top to bottom. */
    val pumpOrderBonus: Double = 1.0,
    /** A rate close to the last price paid or the city price. */
    val rateHintBonus: Double = 2.0,
    val rateHintTolerancePercent: Int = 15,
    val repairPenalty: Double = 0.3,
    /** No point was read, and the reading assumes the usual 2 decimals. */
    val assumedPointPenalty: Double = 0.6,
    /** No point was read, and the reading assumes 1 or 3 decimals. */
    val unusualPointPenalty: Double = 0.9,
    /** No point was read, and the reading takes the number as whole rupees or litres. */
    val wholeNumberPenalty: Double = 0.3,
    val joinedPenalty: Double = 0.4,
    /** Odometer: the shortest digit run that can be an odometer (PRD F-15: ≥4). */
    val minOdometerDigits: Int = 4,
    val odometerDigitWeight: Double = 1.0,
    val odometerKmRowBonus: Double = 3.0,
    val odometerKmNearbyBonus: Double = 1.5,
    val odometerOdoLabelBonus: Double = 3.0,
    /** Trip meters and range readouts sit next to "km" too. */
    val odometerTripPenalty: Double = 5.0,
    val odometerPredictionBonus: Double = 3.0,
    val odometerPredictionTolerancePercent: Int = 10,
) {
    private companion object {
        const val TOLERANCE_PAISE = 100L
        const val MIN_RATE_RUPEES = 40L
        const val MAX_RATE_RUPEES = 250L
        const val MIN_TOTAL_RUPEES = 10L
        const val MAX_TOTAL_RUPEES = 100_000L
    }
}

package app.fuelwise.core.domain.scan

import app.fuelwise.core.model.Money

/**
 * Pump meter scan (PRD F-15): the amount ₹, volume L/kg and rate ₹/unit on a dispenser display. [rateHint] (last
 * price paid or the city price) breaks ties between readings; it never fills a field by itself.
 */
object PumpMeterParser {
    fun parse(lines: List<OcrLine>, rateHint: Money? = null, tuning: ScanTuning = ScanTuning()): TriangleReading =
        TriangleReader(tuning, TriangleReader.Layout.PumpDisplay).read(OcrRows.of(lines), rateHint)
}

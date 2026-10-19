package app.fuelwise.core.domain.scan

import kotlin.math.abs

/**
 * Odometer scan (PRD F-15): the longest digit run of at least 4 digits near the "km" glyph. Trip meters and range
 * readouts sit next to "km" too, so their labels count against a run. The result is always confirmed by the user.
 */
object OdometerParser {
    private val KM = Regex("(?<![A-Z])(KM|KMS|KRN|KN)(?![A-Z/])")
    private val ODO = Regex("(?<![A-Z])(ODO|ODOMETER|TOTAL)(?![A-Z])")
    private val TRIP = Regex("(?<![A-Z])(TRIP|TRP|RANGE|DTE|AVG|AVERAGE)(?![A-Z])")
    private const val PERCENT = 100.0

    private data class Candidate(val km: Long, val box: OcrBox?, val score: Double)

    /** [predictedKm] (PRD F-02.4) favours a reading close to where the odometer should be. */
    fun parse(lines: List<OcrLine>, predictedKm: Double? = null, tuning: ScanTuning = ScanTuning()): Scanned<Long>? {
        val rows = OcrRows.of(lines)
        val kmRows = rows.filter { KM.containsMatchIn(it.text.uppercase()) }.map { it.index }.toSet()
        return rows.flatMap { row -> candidates(row, kmRows, predictedKm, tuning) }
            .maxByOrNull { it.score }
            ?.let { Scanned(it.km, it.box) }
    }

    private fun candidates(row: OcrRow, kmRows: Set<Int>, predictedKm: Double?, tuning: ScanTuning): List<Candidate> {
        val upper = row.text.uppercase()
        val tokens = OcrNumbers.scan(row)
        // Odometers show whole km or tenths; two or more decimals is a price or a quantity.
        val runs = tokens.filter { !it.joined && (it.decimals ?: 0) <= 1 }.map { it.integerDigits to it.repairs } +
            thousandsJoined(row, tokens)
        return runs.filter { (digits, _) -> digits.length >= tuning.minOdometerDigits }.mapNotNull { (digits, repairs) ->
            val km = digits.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            var score = digits.length * tuning.odometerDigitWeight - repairs * tuning.repairPenalty
            score += when {
                row.index in kmRows -> tuning.odometerKmRowBonus
                row.index - 1 in kmRows || row.index + 1 in kmRows -> tuning.odometerKmNearbyBonus
                else -> 0.0
            }
            if (ODO.containsMatchIn(upper)) score += tuning.odometerOdoLabelBonus
            if (TRIP.containsMatchIn(upper)) score -= tuning.odometerTripPenalty
            if (predictedKm != null && predictedKm > 0) {
                val off = abs(km - predictedKm) * PERCENT / predictedKm
                if (off <= tuning.odometerPredictionTolerancePercent) score += tuning.odometerPredictionBonus
            }
            Candidate(km, row.box, score)
        }
    }

    /** Some clusters space thousands apart ("42 318"); the decimal-join reading isn't an odometer, this is. */
    private fun thousandsJoined(row: OcrRow, tokens: List<NumberToken>): List<Pair<String, Int>> =
        tokens.filter { !it.joined }.sortedBy { it.start }.zipWithNext().mapNotNull { (a, b) ->
            val gap = row.text.substring(a.end, b.start)
            val fits = a.decimals == null && (b.decimals ?: 0) <= 1 && a.digits.length in 1..THOUSANDS &&
                b.integerDigits.length == THOUSANDS && gap.isNotEmpty() && gap.isBlank()
            if (fits) a.digits + b.integerDigits to a.repairs + b.repairs + 1 else null
        }

    private const val THOUSANDS = 3
}

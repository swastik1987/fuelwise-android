package app.fuelwise.core.domain.scan

import app.fuelwise.core.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Amount, quantity and rate read from a pump display or receipt (PRD F-15). [validated] means the three passed the
 * price triangle check (|q × p − t| ≤ ₹1), so they may be auto-filled; otherwise each field must be confirmed.
 */
data class TriangleReading(
    val total: Scanned<Money>? = null,
    val quantity: Scanned<Double>? = null,
    val unitPrice: Scanned<Money>? = null,
    val validated: Boolean = false,
) {
    val isEmpty: Boolean get() = total == null && quantity == null && unitPrice == null
}

/**
 * Finds the amount/quantity/rate triple in recognised rows. Every number gets readings (with and without an assumed
 * decimal point) and a label hint; every triple of distinct numbers is scored on labels, plausibility and the price
 * triangle. A triple that passes the triangle wins; otherwise only labelled numbers are offered, unvalidated.
 */
internal class TriangleReader(private val tuning: ScanTuning, private val layout: Layout) {
    enum class Layout { PumpDisplay, Receipt }

    private data class Candidate(
        val token: NumberToken,
        val value: BigDecimal,
        val penalty: Double,
        val hint: Role?,
        val hintWeight: Double,
    ) {
        val paise: Long get() = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

        fun labelScore(role: Role, contradiction: Double): Double = when (hint) {
            null -> 0.0
            role -> hintWeight
            else -> -hintWeight * contradiction
        }
    }

    private data class Triple(val total: Candidate, val quantity: Candidate, val rate: Candidate, val fits: Boolean)

    fun read(rows: List<OcrRow>, rateHint: Money?): TriangleReading {
        val candidates = candidates(rows)
        val totals = candidates.filter { it.paise in tuning.minTotal.paise..tuning.maxTotal.paise }.top(Role.Total)
        val quantities = candidates.filter { it.value.toDouble() in tuning.minQuantity..tuning.maxQuantity }
            .top(Role.Quantity)
        val rates = candidates.filter { it.paise in tuning.minRate.paise..tuning.maxRate.paise }.top(Role.Rate)
        val best = triples(totals, quantities, rates).maxByOrNull { score(it, rateHint) }
        best?.takeIf { it.fits }?.let { return it.reading() }
        return labelledOnly(totals, quantities, rates)
    }

    /** Every triple of three distinct numbers. */
    private fun triples(totals: List<Candidate>, quantities: List<Candidate>, rates: List<Candidate>) = sequence {
        for (t in totals) {
            for (q in quantities.filter { it.token != t.token }) {
                rates.filter { it.token != t.token && it.token != q.token }
                    .forEach { r -> yield(Triple(t, q, r, fits(t, q, r))) }
            }
        }
    }

    private fun candidates(rows: List<OcrRow>): List<Candidate> {
        val rowTokens = rows.map(OcrNumbers::scan)
        val rowLabels = rows.mapIndexed { i, row ->
            // A letter read as a digit can also look like a label ("l9.43" is not "L"); numbers win.
            ScanLabels.find(row.text).filter { label ->
                rowTokens[i].none { it.start < label.end && label.start < it.end }
            }
        }
        return rows.flatMapIndexed { i, row ->
            rowTokens[i].flatMap { token ->
                val (hint, weight) = hintFor(token, row.index, rowLabels) ?: return@flatMap emptyList()
                readings(token).map { (value, penalty) -> Candidate(token, value, penalty, hint, weight) }
            }
        }
    }

    /** The label hint for [token], or null when its label says to ignore it. A missing label gives (null, 0). */
    private fun hintFor(token: NumberToken, row: Int, rowLabels: List<List<Label>>): Pair<Role?, Double>? {
        val same = ScanLabels.nearest(rowLabels[row], token.start, token.end)
        if (same != null) {
            return if (same.role == Role.Ignore) null else same.role to tuning.sameRowLabelWeight
        }
        val neighbours = when (layout) {
            Layout.PumpDisplay -> listOf(row - 1, row + 1)

            // A receipt prints the value under its label when the label is long ("Sale Amount (Rs)").
            Layout.Receipt -> listOf(row - 1)
        }
        val nearby = neighbours.mapNotNull { rowLabels.getOrNull(it)?.firstOrNull() }
            .firstOrNull { it.role != Role.Ignore }
        return nearby?.role to if (nearby != null) tuning.nearbyLabelWeight else 0.0
    }

    /** Readings of [token] with the penalty for each assumption. Receipts print their points; pump LEDs drop them. */
    private fun readings(token: NumberToken): List<Pair<BigDecimal, Double>> {
        val base = tuning.repairPenalty * token.repairs + if (token.joined) tuning.joinedPenalty else 0.0
        val decimals = token.decimals
        return when {
            decimals != null -> listOf(token.valueWith(decimals) to base)

            layout == Layout.Receipt -> listOf(token.valueWith(0) to base)

            else -> listOf(
                token.valueWith(0) to base + tuning.wholeNumberPenalty,
                token.valueWith(USUAL_DECIMALS) to base + tuning.assumedPointPenalty,
                token.valueWith(ONE_DECIMAL) to base + tuning.unusualPointPenalty,
                token.valueWith(THREE_DECIMALS) to base + tuning.unusualPointPenalty,
            )
        }
    }

    private fun List<Candidate>.top(role: Role): List<Candidate> =
        sortedByDescending { it.labelScore(role, tuning.contradictionFactor) - it.penalty }.take(MAX_PER_ROLE)

    private fun fits(t: Candidate, q: Candidate, r: Candidate): Boolean {
        val computed = q.value.multiply(BigDecimal.valueOf(r.paise)).setScale(0, RoundingMode.HALF_UP).toLong()
        return abs(computed - t.paise) <= tuning.triangleTolerance.paise
    }

    private fun score(triple: Triple, rateHint: Money?): Double {
        val c = tuning.contradictionFactor
        var score = triple.total.labelScore(Role.Total, c) - triple.total.penalty +
            triple.quantity.labelScore(Role.Quantity, c) - triple.quantity.penalty +
            triple.rate.labelScore(Role.Rate, c) - triple.rate.penalty
        if (triple.fits) score += tuning.triangleFitBonus
        val rows = listOf(triple.total, triple.quantity, triple.rate).map { it.token.row }
        if (layout == Layout.PumpDisplay && rows == rows.sorted() && rows.distinct().size == rows.size) {
            score += tuning.pumpOrderBonus
        }
        if (rateHint != null && rateHint.paise > 0) {
            // Graded, so the closer of two plausible rates wins.
            val off = abs(triple.rate.paise - rateHint.paise) * PERCENT / rateHint.paise
            val tolerance = tuning.rateHintTolerancePercent.toDouble()
            if (off <= tolerance) score += tuning.rateHintBonus * (1 - off / tolerance)
        }
        return score
    }

    /** No consistent triple: offer only numbers a label vouches for, each to be confirmed by the user. */
    private fun labelledOnly(
        totals: List<Candidate>,
        quantities: List<Candidate>,
        rates: List<Candidate>,
    ): TriangleReading {
        val used = mutableSetOf<NumberToken>()
        fun pick(list: List<Candidate>, role: Role): Candidate? = list
            .filter { it.hint == role && it.token !in used }
            .maxByOrNull { it.hintWeight - it.penalty }
            ?.also { used += it.token }
        val rate = pick(rates, Role.Rate)
        val quantity = pick(quantities, Role.Quantity)
        val total = pick(totals, Role.Total)
        return TriangleReading(
            total = total?.let { Scanned(Money(it.paise), it.token.box) },
            quantity = quantity?.let { Scanned(it.quantity(), it.token.box) },
            unitPrice = rate?.let { Scanned(Money(it.paise), it.token.box) },
            validated = false,
        )
    }

    private fun Triple.reading() = TriangleReading(
        total = Scanned(Money(total.paise), total.token.box),
        quantity = Scanned(quantity.quantity(), quantity.token.box),
        unitPrice = Scanned(Money(rate.paise), rate.token.box),
        validated = true,
    )

    private fun Candidate.quantity(): Double = value.setScale(QUANTITY_DECIMALS, RoundingMode.HALF_UP).toDouble()

    private companion object {
        const val MAX_PER_ROLE = 32
        const val USUAL_DECIMALS = 2
        const val ONE_DECIMAL = 1
        const val THREE_DECIMALS = 3
        const val QUANTITY_DECIMALS = 2
        const val PERCENT = 100.0
    }
}

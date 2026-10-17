package app.fuelwise.core.domain.engine

import javax.inject.Inject

/** One EV charge session (PRD F-02.5). SoC values are percentages 0–100; energy is kWh billed or metered. */
data class ChargeSession(
    val id: String,
    val odometerKm: Double,
    val dateTimeUtcMs: Long,
    val energyBilledKwh: Double,
    val socStartPct: Double? = null,
    val socEndPct: Double? = null,
    val chargedToFull: Boolean = false,
    val missedPrevious: Boolean = false,
)

/**
 * EV efficiency, both ways (PRD F-03.4):
 * - [batteryToWheel]: SoC method, energy that left the battery per km. Primary when SoC and capacity are known.
 * - [wallToWheel]: full-charge method on billed kWh, so it includes charging losses. The fallback.
 * - [chargingEfficiency]: energy that reached the battery ÷ energy billed, per session id (0.9 = 90%).
 */
data class EvReport(
    val batteryToWheel: ConsumptionReport,
    val wallToWheel: ConsumptionReport,
    val chargingEfficiency: Map<String, Double>,
)

class ComputeEvEfficiency @Inject constructor() {
    private val fullTank = ComputeConsumption()

    operator fun invoke(sessions: List<ChargeSession>, usableCapacityKwh: Double?): EvReport {
        val sorted = sessions.sortedWith(SESSION_ORDER)
        return EvReport(
            batteryToWheel = socMethod(sorted, usableCapacityKwh),
            wallToWheel = fullTank(sorted.map { it.asFullChargeFill() }),
            chargingEfficiency = sorted.mapNotNull { s -> chargingEfficiency(s, usableCapacityKwh)?.let { s.id to it } }
                .toMap(),
        )
    }

    /** energy used = (prev.endSoC − curr.startSoC) × capacity, over distance = curr.odo − prev.odo. */
    private fun socMethod(sorted: List<ChargeSession>, capacityKwh: Double?): ConsumptionReport {
        var total: Interval? = null
        val entries = sorted.mapIndexed { index, curr ->
            val prev = sorted.getOrNull(index - 1)
            val result: IntervalResult = when {
                prev == null -> IntervalResult.Baseline

                curr.missedPrevious -> IntervalResult.Invalid(IntervalResult.Invalid.Reason.MissedPrevious)

                capacityKwh == null || prev.socEndPct == null || curr.socStartPct == null ->
                    IntervalResult.Invalid(IntervalResult.Invalid.Reason.MissingSoc)

                curr.odometerKm - prev.odometerKm <= 0.0 ->
                    IntervalResult.Invalid(IntervalResult.Invalid.Reason.NoDistance)

                prev.socEndPct <= curr.socStartPct ->
                    IntervalResult.Invalid(IntervalResult.Invalid.Reason.InconsistentSoc)

                else -> {
                    val used = (prev.socEndPct - curr.socStartPct) / PERCENT * capacityKwh
                    val interval = Interval(curr.odometerKm - prev.odometerKm, used)
                    total = total?.plus(interval) ?: interval
                    IntervalResult.Measured(interval)
                }
            }
            ConsumptionReport.Entry(curr.id, result)
        }
        return ConsumptionReport(entries, total)
    }

    /** ((endSoC − startSoC) × capacity) ÷ kWh billed. Null when any input is missing or meaningless. */
    private fun chargingEfficiency(session: ChargeSession, capacityKwh: Double?): Double? {
        val start = session.socStartPct ?: return null
        val end = session.socEndPct ?: return null
        if (capacityKwh == null || session.energyBilledKwh <= 0.0 || end <= start) return null
        return (end - start) / PERCENT * capacityKwh / session.energyBilledKwh
    }

    private fun ChargeSession.asFullChargeFill() = FillUp(
        id = id,
        odometerKm = odometerKm,
        dateTimeUtcMs = dateTimeUtcMs,
        quantity = energyBilledKwh,
        isFull = chargedToFull || (socEndPct ?: 0.0) >= FULL_SOC_PCT,
        missedPrevious = missedPrevious,
    )

    private companion object {
        const val PERCENT = 100.0

        /** A session ending at or above this SoC counts as "charged to full" (PRD F-03.4 fallback). */
        const val FULL_SOC_PCT = 99.0

        val SESSION_ORDER = compareBy<ChargeSession>({ it.odometerKm }, { it.dateTimeUtcMs }, { it.id })
    }
}

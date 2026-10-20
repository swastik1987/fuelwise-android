package app.fuelwise.core.domain.prices

import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import javax.inject.Inject
import kotlin.math.abs

/** A home-city price move worth a notification: "Petrol in Bengaluru up ₹1.20/L today." */
data class PriceChange(val fuel: PriceFuel, val before: Money, val after: Money) {
    val delta: Money get() = after - before
}

/**
 * Price alerts (PRD F-09.5): opt-in, and only for a feed update. Compares the home city's prices in effect before
 * and after one feed run; a move of [THRESHOLD_PAISE] (₹1 per L or kg) or more alerts. Prices the user types never
 * alert (they already know), and a fuel with no earlier price has nothing to compare with.
 */
class DetectPriceAlerts @Inject constructor() {
    operator fun invoke(
        enabled: Boolean,
        fromFeed: Boolean,
        before: Map<PriceFuel, Money>,
        after: Map<PriceFuel, Money>,
    ): List<PriceChange> {
        if (!enabled || !fromFeed) return emptyList()
        return PriceFuel.entries.mapNotNull { fuel ->
            val old = before[fuel] ?: return@mapNotNull null
            val new = after[fuel] ?: return@mapNotNull null
            PriceChange(fuel, old, new).takeIf { abs(it.delta.paise) >= THRESHOLD_PAISE }
        }
    }

    companion object {
        const val THRESHOLD_PAISE = 100L
    }
}

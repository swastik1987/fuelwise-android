package app.fuelwise.core.ui.price

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.domain.prices.FairPrice
import app.fuelwise.core.model.Money
import app.fuelwise.core.ui.R
import app.fuelwise.core.ui.format.IndianNumberFormat
import kotlin.math.abs

/** "You paid ₹0.42/L above the city price" (PRD F-09.3, design.md screen 7). Neutral tone in every case. */
@Composable
fun fairPriceText(fair: FairPrice): String {
    val unit = stringResource(PriceLabels.unit(fair.city.fuel))
    val delta = IndianNumberFormat.rupees(Money(abs(fair.delta.paise)))
    return when (fair.verdict) {
        FairPrice.Verdict.Above -> stringResource(R.string.fair_price_above, delta, unit)
        FairPrice.Verdict.Below -> stringResource(R.string.fair_price_below, delta, unit)
        FairPrice.Verdict.Same -> stringResource(R.string.fair_price_same)
    }
}

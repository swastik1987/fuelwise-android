package app.fuelwise.feature.refuel

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.component.Delta
import app.fuelwise.core.designsystem.component.FwDeltaChip
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwMotion
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.entry.FillReward
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.efficiencyUnit
import app.fuelwise.core.ui.format.efficiencyValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Post-save reward (PRD F-02.7, design.md screen 7). The number lands with a 0.92 → 1.0 scale and one confirm
 * haptic; the sheet dismisses itself after 3 seconds, on tap, or on a downward swipe.
 */
@Composable
internal fun RewardOverlay(
    reward: FillReward,
    vehicleName: String,
    quantity: QuantityUnit,
    units: DisplayUnits,
    onDismiss: () -> Unit,
    // "You paid ₹0.42/L above the city price" (PRD F-09.3); null without a city price for that day.
    fairPriceLine: String? = null,
) {
    val view = LocalView.current
    val scale = remember { Animatable(LAND_FROM) }
    LaunchedEffect(reward) {
        // CONFIRM exists from Android 11; older phones get the standard virtual-key tick.
        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(confirm)
        scale.animateTo(1f, tween(FwMotion.EMPHASIS))
    }
    LaunchedEffect(reward) {
        delay(if (reward is FillReward.Measured || reward is FillReward.Baseline) HOLD_MS else SHORT_HOLD_MS)
        onDismiss()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(FwTheme.colors.scrim.copy(alpha = SCRIM_ALPHA))
            .clickable(onClick = onDismiss)
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag > SWIPE_PX) onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(FwTheme.colors.surface, FwTheme.radius.sheet)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).background(FwTheme.colors.ink3, FwTheme.radius.pill))
            }
            when (reward) {
                is FillReward.Measured -> Measured(reward, vehicleName, quantity, units, scale.value)

                FillReward.Baseline -> Message(
                    R.string.reward_baseline_overline,
                    R.string.reward_baseline_title,
                    R.string.reward_baseline_body,
                )

                FillReward.Partial -> Message(
                    R.string.reward_saved_overline,
                    R.string.reward_partial_title,
                    R.string.reward_partial_body,
                )

                FillReward.NotMeasured -> Message(
                    R.string.reward_saved_overline,
                    R.string.reward_not_measured_title,
                    R.string.reward_not_measured_body,
                )
            }
            fairPriceLine?.let { Text(it, style = FwTheme.type.body, color = FwTheme.colors.ink2) }
            Text(
                stringResource(R.string.reward_dismiss),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Measured(
    reward: FillReward.Measured,
    vehicleName: String,
    quantity: QuantityUnit,
    units: DisplayUnits,
    scale: Float,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FwOverline(stringResource(R.string.reward_overline, vehicleName), Modifier.weight(1f))
        Icon(painterResource(FwIcons.Check), null, tint = FwTheme.colors.ink2, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.reward_saved), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
    }
    Row(
        Modifier.scale(scale),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            efficiencyValue(reward.interval.kmPerUnit, quantity, units),
            style = FwTheme.type.display,
            color = FwTheme.colors.ink,
        )
        Text(
            efficiencyUnit(quantity, units),
            style = FwTheme.type.h3,
            color = FwTheme.colors.ink2,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    reward.changeVsAverage?.let { change ->
        val percent = (abs(change) * PERCENT).roundToInt()
        FwDeltaChip(
            Delta(
                label = stringResource(R.string.reward_vs_average, percent),
                // The arrow follows the number shown: in L/100 km a better tank reads lower.
                direction = when {
                    percent == 0 -> Delta.Direction.Flat
                    (change > 0) == units.higherIsBetter -> Delta.Direction.Up
                    else -> Delta.Direction.Down
                },
                isImprovement = if (percent == 0) null else change > 0,
            ),
        )
    }
    Text(
        stringResource(
            R.string.reward_cost_per_km,
            IndianNumberFormat.rupees(reward.costPerKm),
            IndianNumberFormat.decimal(reward.interval.distanceKm, 0),
        ),
        style = FwTheme.type.body,
        color = FwTheme.colors.ink2,
    )
    if (reward.unusual) {
        Text(stringResource(R.string.reward_unusual), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
    }
}

@Composable
private fun Message(overline: Int, title: Int, body: Int) {
    FwOverline(stringResource(overline))
    Text(stringResource(title), style = FwTheme.type.h1, color = FwTheme.colors.ink)
    Text(stringResource(body), style = FwTheme.type.body, color = FwTheme.colors.ink2)
}

private const val LAND_FROM = 0.92f
private const val HOLD_MS = 3_000L
private const val SHORT_HOLD_MS = 1_500L
private const val SCRIM_ALPHA = 0.6f
private const val SWIPE_PX = 12f
private const val PERCENT = 100

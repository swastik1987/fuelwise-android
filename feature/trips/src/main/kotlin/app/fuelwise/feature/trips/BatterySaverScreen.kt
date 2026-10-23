package app.fuelwise.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.PhoneBrand
import app.fuelwise.core.location.BatterySaverHelp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where "Done — test it" is. */
internal enum class BatteryCheck { NotRun, Checking, Passed, Blocked }

internal data class BatteryActions(
    val onOpenSettings: () -> Unit = {},
    val onTest: () -> Unit = {},
    val onFinish: () -> Unit = {},
)

/** Opened on its own from Home's amber pill or Settings (PRD F-21.7 health check). */
@Composable
internal fun BatterySaverScreen(onClose: () -> Unit) {
    val (check, actions) = rememberBatteryCheck(onFinish = onClose)
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(title = "", onBack = onClose, backContentDescription = stringResource(R.string.trips_back))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = FwTheme.spacing.gutter),
        ) {
            BatterySaverBody(BatterySaverHelp.brand, check)
        }
        BatterySaverButtons(check, actions)
    }
}

/** The check's state and actions, shared by setup step 4 and the standalone screen. */
@Composable
internal fun rememberBatteryCheck(onFinish: () -> Unit): Pair<BatteryCheck, BatteryActions> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var check by rememberSaveable { mutableStateOf(BatteryCheck.NotRun) }
    val actions = BatteryActions(
        onOpenSettings = {
            BatterySaverHelp.openSettings(context)
            check = BatteryCheck.NotRun
        },
        onTest = {
            check = BatteryCheck.Checking
            scope.launch {
                // A short pause so the result reads as a check rather than a flicker.
                delay(CHECK_MS)
                check = if (BatterySaverHelp.isUnrestricted(context)) BatteryCheck.Passed else BatteryCheck.Blocked
            }
        },
        onFinish = onFinish,
    )
    return check to actions
}

/** Phone-maker steps (design.md screen 33): the brand at top, 2–3 steps, and the check's result. */
@Composable
internal fun ColumnScope.BatterySaverBody(brand: PhoneBrand, check: BatteryCheck) {
    // The name the phone's settings list the app under.
    val context = LocalContext.current
    val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
    FwOverline(stringResource(brandName(brand)), Modifier.padding(top = FwTheme.spacing.s))
    Text(
        stringResource(R.string.battery_title),
        style = FwTheme.type.h1,
        color = FwTheme.colors.ink,
        modifier = Modifier.padding(top = FwTheme.spacing.s).semantics { heading() },
    )
    Text(
        stringResource(R.string.battery_body),
        style = FwTheme.type.body,
        color = FwTheme.colors.ink2,
        modifier = Modifier.padding(top = FwTheme.spacing.s),
    )
    Column(
        Modifier.padding(top = FwTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
    ) {
        brandSteps(brand).forEachIndexed { index, step ->
            FwCard(Modifier.fillMaxWidth()) {
                FwOverline(stringResource(R.string.battery_step, index + 1))
                Text(
                    stringResource(step, appName),
                    style = FwTheme.type.label,
                    color = FwTheme.colors.ink,
                    modifier = Modifier.padding(top = FwTheme.spacing.xxs),
                )
            }
        }
    }
    CheckResult(check)
}

@Composable
private fun CheckResult(check: BatteryCheck) {
    val (text, icon, color) = when (check) {
        BatteryCheck.NotRun -> return
        BatteryCheck.Checking -> Triple(R.string.battery_checking, FwIcons.Schedule, FwTheme.colors.ink2)
        BatteryCheck.Passed -> Triple(R.string.battery_passed, FwIcons.CheckCircleFilled, FwTheme.colors.positive)
        BatteryCheck.Blocked -> Triple(R.string.battery_blocked, FwIcons.BatteryFull, FwTheme.colors.warning)
    }
    Row(
        Modifier.padding(top = FwTheme.spacing.l).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = color)
        Text(
            stringResource(text),
            style = FwTheme.type.label,
            color = FwTheme.colors.ink,
            modifier = Modifier.padding(start = FwTheme.spacing.s),
        )
    }
}

@Composable
internal fun BatterySaverButtons(check: BatteryCheck, actions: BatteryActions) {
    Column(
        Modifier.navigationBarsPadding().padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
    ) {
        BatteryButtons(check, actions)
    }
}

/** The buttons alone, for a caller that already lays out its bottom bar (setup). */
@Composable
internal fun BatteryButtons(check: BatteryCheck, actions: BatteryActions) {
    when (check) {
        BatteryCheck.Passed -> FwPrimaryButton(
            stringResource(R.string.battery_finish),
            actions.onFinish,
            Modifier.fillMaxWidth(),
        )

        else -> {
            FwPrimaryButton(
                stringResource(R.string.auto_open_settings),
                actions.onOpenSettings,
                Modifier.fillMaxWidth(),
            )
            FwSecondaryButton(
                stringResource(R.string.battery_test),
                actions.onTest,
                Modifier.fillMaxWidth(),
                enabled = check != BatteryCheck.Checking,
            )
            FwTextButton(stringResource(R.string.battery_skip), actions.onFinish, Modifier.fillMaxWidth())
        }
    }
}

private fun brandName(brand: PhoneBrand) = when (brand) {
    PhoneBrand.Xiaomi -> R.string.battery_brand_xiaomi
    PhoneBrand.Samsung -> R.string.battery_brand_samsung
    PhoneBrand.Oppo -> R.string.battery_brand_oppo
    PhoneBrand.Vivo -> R.string.battery_brand_vivo
    PhoneBrand.Huawei -> R.string.battery_brand_huawei
    PhoneBrand.Other -> R.string.battery_brand_other
}

private fun brandSteps(brand: PhoneBrand): List<Int> = when (brand) {
    PhoneBrand.Xiaomi -> listOf(R.string.battery_xiaomi_1, R.string.battery_xiaomi_2, R.string.battery_xiaomi_3)
    PhoneBrand.Samsung -> listOf(R.string.battery_samsung_1, R.string.battery_samsung_2, R.string.battery_samsung_3)
    PhoneBrand.Oppo -> listOf(R.string.battery_oppo_1, R.string.battery_oppo_2)
    PhoneBrand.Vivo -> listOf(R.string.battery_vivo_1, R.string.battery_vivo_2)
    PhoneBrand.Huawei -> listOf(R.string.battery_huawei_1, R.string.battery_huawei_2)
    PhoneBrand.Other -> listOf(R.string.battery_other_1, R.string.battery_other_2)
}

private const val CHECK_MS = 1_500L

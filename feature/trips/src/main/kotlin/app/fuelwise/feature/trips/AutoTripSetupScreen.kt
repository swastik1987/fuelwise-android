package app.fuelwise.feature.trips

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.location.BatterySaverHelp
import app.fuelwise.core.location.rememberCarBluetoothLinker

/**
 * Where setup is. [Disclosure] is Play's prominent disclosure and always comes first, before any permission prompt
 * (PRD F-21.3, §9). [Denied] is the one-tap fallback when "Allow all the time" is refused. [Car] is the optional car
 * Bluetooth step (Android 12+, with at least one car). [Battery] is the phone-maker battery saver fix (PRD F-21.7).
 */
internal enum class SetupStep { Disclosure, Activity, Location, Car, Battery, Denied }

internal data class SetupActions(
    val onClose: () -> Unit = {},
    val onTurnOn: () -> Unit = {},
    val onAllowActivity: () -> Unit = {},
    val onAllowLocation: () -> Unit = {},
    val onOneTap: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
    /** Opens the system chooser to link this vehicle's car Bluetooth. */
    val onLinkCar: (vehicleId: String) -> Unit = {},
    val onContinue: () -> Unit = {},
    val battery: BatteryActions = BatteryActions(),
)

/** Automatic trips setup (design.md screen 32). Opened from Trips or Settings, never on first run. */
@Composable
internal fun AutoTripSetupScreen(onClose: () -> Unit, viewModel: AutoTripSetupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val previewVehicle by viewModel.previewVehicle.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(SetupStep.Disclosure) }
    var activityRefused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onClose() } }
    // Which vehicle the system chooser is linking; the chooser hands back only the car.
    var linkingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val linker = rememberCarBluetoothLinker { link ->
        linkingFor?.let { viewModel.linkCar(it, link) }
        linkingFor = null
    }
    val carStep = linker != null && cars.isNotEmpty()
    // "Allow all the time" granted: the optional car step, or done.
    val afterLocation = { step = if (carStep) SetupStep.Car else SetupStep.Battery }
    val (battery, batteryActions) = rememberBatteryCheck(onFinish = { viewModel.finish(oneTap = false) })

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterLocation() else step = SetupStep.Denied
    }
    // Android 11+ won't offer "all the time" in the first dialog: while-in-use first, then the settings page.
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        when {
            context.hasBackgroundLocation() -> afterLocation()
            !context.granted(Manifest.permission.ACCESS_FINE_LOCATION) -> step = SetupStep.Denied
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> background.launch(BACKGROUND_LOCATION)
            else -> step = SetupStep.Denied
        }
    }
    val activity = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.hasActivityRecognition()) step = SetupStep.Location else activityRefused = true
    }
    val askLocation = {
        when {
            context.hasBackgroundLocation() -> afterLocation()

            context.granted(
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                background.launch(BACKGROUND_LOCATION)

            else -> foreground.launch(foregroundLocationPermissions())
        }
    }
    // Back from the app's settings page: "Allow all the time" may have been picked there.
    LifecycleResumeEffect(step) {
        if (step == SetupStep.Denied && context.hasBackgroundLocation()) afterLocation()
        onPauseOrDispose {}
    }
    val back = {
        step = when (step) {
            SetupStep.Disclosure -> SetupStep.Disclosure.also { onClose() }
            SetupStep.Activity -> SetupStep.Disclosure
            SetupStep.Location -> if (needsActivityStep()) SetupStep.Activity else SetupStep.Disclosure
            SetupStep.Car, SetupStep.Denied -> SetupStep.Location
            SetupStep.Battery -> if (carStep) SetupStep.Car else SetupStep.Location
        }
    }
    BackHandler(enabled = step != SetupStep.Disclosure) { back() }

    AutoTripSetupContent(
        step = step,
        activityRefused = activityRefused,
        previewVehicle = previewVehicle,
        cars = cars,
        carStep = carStep,
        battery = battery,
        actions = SetupActions(
            onClose = onClose,
            onTurnOn = {
                step = when {
                    needsActivityStep() && !context.hasActivityRecognition() -> SetupStep.Activity
                    else -> SetupStep.Location
                }
            },
            onAllowActivity = {
                if (activityRefused) context.openAppSettings() else activity.launch(activityPermissions())
            },
            onAllowLocation = askLocation,
            onOneTap = { viewModel.finish(oneTap = true) },
            onRetry = { context.openAppSettings() },
            onBack = back,
            onLinkCar = { id ->
                linkingFor = id
                linker?.invoke()
            },
            onContinue = { step = SetupStep.Battery },
            battery = batteryActions,
        ),
    )
}

@Composable
internal fun AutoTripSetupContent(
    step: SetupStep,
    activityRefused: Boolean,
    previewVehicle: String?,
    actions: SetupActions,
    cars: List<SetupCar> = emptyList(),
    carStep: Boolean = false,
    battery: BatteryCheck = BatteryCheck.NotRun,
) {
    val total = (if (needsActivityStep()) 1 else 0) + 1 + (if (carStep) 1 else 0) + 1
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        if (step == SetupStep.Disclosure) {
            FwTopBar(title = "", onBack = actions.onClose, backContentDescription = stringResource(R.string.auto_close))
        } else {
            FwTopBar(title = "", onBack = actions.onBack, backContentDescription = stringResource(R.string.trips_back))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.gutter),
        ) {
            when (step) {
                SetupStep.Disclosure -> Disclosure()

                SetupStep.Activity -> ActivityStep(activityRefused, total)

                SetupStep.Location -> LocationStep(total)

                SetupStep.Car -> CarStep(cars, total - 1, total, actions.onLinkCar)

                SetupStep.Battery -> {
                    StepLabel(total, total)
                    BatterySaverBody(BatterySaverHelp.brand, battery)
                }

                SetupStep.Denied -> DeniedStep(previewVehicle)
            }
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
        ) {
            when (step) {
                SetupStep.Disclosure -> {
                    FwPrimaryButton(stringResource(R.string.auto_turn_on), actions.onTurnOn, Modifier.fillMaxWidth())
                    FwTextButton(stringResource(R.string.auto_not_now), actions.onClose, Modifier.fillMaxWidth())
                }

                SetupStep.Activity -> FwPrimaryButton(
                    stringResource(if (activityRefused) R.string.auto_open_settings else R.string.auto_allow),
                    actions.onAllowActivity,
                    Modifier.fillMaxWidth(),
                )

                SetupStep.Location -> FwPrimaryButton(
                    stringResource(R.string.auto_open_settings),
                    actions.onAllowLocation,
                    Modifier.fillMaxWidth(),
                )

                SetupStep.Car -> FwPrimaryButton(
                    stringResource(R.string.auto_continue),
                    actions.onContinue,
                    Modifier.fillMaxWidth(),
                )

                SetupStep.Battery -> BatteryButtons(battery, actions.battery)

                SetupStep.Denied -> {
                    FwPrimaryButton(stringResource(R.string.auto_one_tap), actions.onOneTap, Modifier.fillMaxWidth())
                    FwTextButton(stringResource(R.string.auto_try_again), actions.onRetry, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Disclosure() {
    IconBadge(FwIcons.Route)
    Headline(stringResource(R.string.auto_disclosure_title))
    // Play's prominent disclosure: names "location" and "background", shown before any permission prompt.
    Text(
        AnnotatedString.fromHtml(stringResource(R.string.auto_disclosure_body)),
        style = FwTheme.type.body,
        color = FwTheme.colors.ink,
        modifier = Modifier.padding(top = FwTheme.spacing.s),
    )
    Column(
        Modifier.padding(top = FwTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        Promise(FwIcons.Car, R.string.auto_promise_driving)
        Promise(FwIcons.Shield, R.string.auto_promise_phone)
        Promise(FwIcons.Schedule, R.string.auto_promise_pause)
    }
}

@Composable
private fun ColumnScope.ActivityStep(refused: Boolean, total: Int) {
    StepLabel(1, total)
    Headline(stringResource(R.string.auto_activity_title))
    Body(stringResource(R.string.auto_activity_body))
    if (refused) Note(stringResource(R.string.auto_activity_refused))
}

@Composable
private fun ColumnScope.LocationStep(total: Int) {
    StepLabel(if (needsActivityStep()) 2 else 1, total)
    Headline(stringResource(R.string.auto_location_title))
    Body(stringResource(R.string.auto_location_body))
    // An illustration of Android's page, not the real thing: which option to pick.
    FwCard(Modifier.padding(top = FwTheme.spacing.l).fillMaxWidth()) {
        FwOverline(stringResource(R.string.auto_location_page))
        Option(stringResource(R.string.auto_location_always), highlighted = true)
        Option(stringResource(R.string.auto_location_while_using))
        Option(stringResource(R.string.auto_location_ask))
        Option(stringResource(R.string.auto_location_deny))
    }
    Note(stringResource(R.string.auto_location_illustration))
}

/** Optional (design.md screen 32, step 3): link each car's Bluetooth; two-wheelers aren't listed. */
@Composable
private fun ColumnScope.CarStep(cars: List<SetupCar>, number: Int, total: Int, onLink: (String) -> Unit) {
    StepLabel(number, total, optional = true)
    Headline(stringResource(R.string.auto_car_title))
    Body(stringResource(R.string.auto_car_body))
    Column(
        Modifier.padding(top = FwTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
    ) {
        cars.forEach { car ->
            FwCard(Modifier.fillMaxWidth(), onClick = { onLink(car.vehicleId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(FwIcons.Car), null, tint = FwTheme.colors.ink2)
                    Spacer(Modifier.size(FwTheme.spacing.s))
                    Column(Modifier.weight(1f)) {
                        Text(car.name, style = FwTheme.type.label, color = FwTheme.colors.ink)
                        Text(
                            car.link?.let { it.name ?: stringResource(R.string.auto_car_linked) }
                                ?: stringResource(R.string.auto_car_not_linked),
                            style = FwTheme.type.caption,
                            color = FwTheme.colors.ink2,
                        )
                    }
                    Text(
                        stringResource(if (car.link == null) R.string.auto_car_link else R.string.auto_car_change),
                        style = FwTheme.type.label,
                        color = FwTheme.colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DeniedStep(previewVehicle: String?) {
    Headline(stringResource(R.string.auto_denied_title))
    Body(stringResource(R.string.auto_denied_body))
    FwOverline(stringResource(R.string.auto_denied_preview), Modifier.padding(top = FwTheme.spacing.l))
    FwCard(Modifier.padding(top = FwTheme.spacing.xs).fillMaxWidth()) {
        Text(
            previewVehicle?.let { stringResource(R.string.auto_denied_preview_vehicle, it) }
                ?: stringResource(R.string.auto_denied_preview_plain),
            style = FwTheme.type.label,
            color = FwTheme.colors.ink,
        )
    }
    Note(stringResource(R.string.auto_denied_fillups))
}

@Composable
private fun IconBadge(icon: Int) {
    Box(
        Modifier
            .padding(top = FwTheme.spacing.m)
            .size(FwTheme.size.fab)
            .background(FwTheme.colors.accentSoft, FwTheme.radius.fab),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = FwTheme.colors.ink, modifier = Modifier.size(FwTheme.size.icon))
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text,
        style = FwTheme.type.h1,
        color = FwTheme.colors.ink,
        modifier = Modifier.padding(top = FwTheme.spacing.l).semantics { heading() },
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = FwTheme.type.body,
        color = FwTheme.colors.ink2,
        modifier = Modifier.padding(top = FwTheme.spacing.s),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = FwTheme.type.caption,
        color = FwTheme.colors.ink2,
        modifier = Modifier.padding(top = FwTheme.spacing.s),
    )
}

@Composable
private fun StepLabel(number: Int, total: Int, optional: Boolean = false) {
    val label = stringResource(R.string.auto_step, number, total)
    FwOverline(
        if (optional) stringResource(R.string.auto_step_optional, label) else label,
        Modifier.padding(top = FwTheme.spacing.s),
    )
}

@Composable
private fun Promise(icon: Int, text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = FwTheme.colors.ink2, modifier = Modifier.size(FwTheme.size.icon))
        Spacer(Modifier.size(FwTheme.spacing.s))
        Text(stringResource(text), style = FwTheme.type.label, color = FwTheme.colors.ink)
    }
}

@Composable
private fun Option(text: String, highlighted: Boolean = false) {
    val modifier = if (highlighted) {
        Modifier.border(FwTheme.size.hairline, FwTheme.colors.accent, FwTheme.radius.tile)
    } else {
        Modifier
    }
    Text(
        text,
        style = if (highlighted) FwTheme.type.label else FwTheme.type.bodyS,
        color = if (highlighted) FwTheme.colors.ink else FwTheme.colors.ink2,
        modifier = Modifier
            .padding(top = FwTheme.spacing.xs)
            .fillMaxWidth()
            .then(modifier)
            .padding(horizontal = FwTheme.spacing.s, vertical = FwTheme.spacing.xs),
    )
}

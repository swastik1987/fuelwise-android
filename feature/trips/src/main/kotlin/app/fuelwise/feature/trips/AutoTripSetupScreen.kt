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

/**
 * Where setup is. [Disclosure] is Play's prominent disclosure and always comes first, before any permission prompt
 * (PRD F-21.3, §9). [Denied] is the one-tap fallback when "Allow all the time" is refused.
 */
internal enum class SetupStep { Disclosure, Activity, Location, Denied }

internal data class SetupActions(
    val onClose: () -> Unit = {},
    val onTurnOn: () -> Unit = {},
    val onAllowActivity: () -> Unit = {},
    val onAllowLocation: () -> Unit = {},
    val onOneTap: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Automatic trips setup (design.md screen 32). Opened from Trips or Settings, never on first run. */
@Composable
internal fun AutoTripSetupScreen(onClose: () -> Unit, viewModel: AutoTripSetupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val previewVehicle by viewModel.previewVehicle.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(SetupStep.Disclosure) }
    var activityRefused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onClose() } }

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.finish(oneTap = false) else step = SetupStep.Denied
    }
    // Android 11+ won't offer "all the time" in the first dialog: while-in-use first, then the settings page.
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        when {
            context.hasBackgroundLocation() -> viewModel.finish(oneTap = false)
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
            context.hasBackgroundLocation() -> viewModel.finish(oneTap = false)

            context.granted(
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                background.launch(BACKGROUND_LOCATION)

            else -> foreground.launch(foregroundLocationPermissions())
        }
    }
    // Back from the app's settings page: "Allow all the time" may have been picked there.
    LifecycleResumeEffect(step) {
        if (step == SetupStep.Denied && context.hasBackgroundLocation()) viewModel.finish(oneTap = false)
        onPauseOrDispose {}
    }
    val back = {
        step = when (step) {
            SetupStep.Disclosure -> SetupStep.Disclosure.also { onClose() }
            SetupStep.Activity -> SetupStep.Disclosure
            SetupStep.Location -> if (needsActivityStep()) SetupStep.Activity else SetupStep.Disclosure
            SetupStep.Denied -> SetupStep.Location
        }
    }
    BackHandler(enabled = step != SetupStep.Disclosure) { back() }

    AutoTripSetupContent(
        step = step,
        activityRefused = activityRefused,
        previewVehicle = previewVehicle,
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
        ),
    )
}

@Composable
internal fun AutoTripSetupContent(
    step: SetupStep,
    activityRefused: Boolean,
    previewVehicle: String?,
    actions: SetupActions,
) {
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
                SetupStep.Activity -> ActivityStep(activityRefused)
                SetupStep.Location -> LocationStep()
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
private fun ColumnScope.ActivityStep(refused: Boolean) {
    StepLabel(1)
    Headline(stringResource(R.string.auto_activity_title))
    Body(stringResource(R.string.auto_activity_body))
    if (refused) Note(stringResource(R.string.auto_activity_refused))
}

@Composable
private fun ColumnScope.LocationStep() {
    StepLabel(if (needsActivityStep()) 2 else 1)
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
private fun StepLabel(number: Int) {
    val total = if (needsActivityStep()) 2 else 1
    FwOverline(stringResource(R.string.auto_step, number, total), Modifier.padding(top = FwTheme.spacing.s))
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

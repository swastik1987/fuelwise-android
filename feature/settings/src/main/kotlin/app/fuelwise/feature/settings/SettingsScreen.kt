package app.fuelwise.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.data.repository.ThemeMode
import app.fuelwise.core.designsystem.component.FwChoiceSheet
import app.fuelwise.core.designsystem.component.FwSectionHeader
import app.fuelwise.core.designsystem.component.FwSettingsRow
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.designsystem.theme.supportsDynamicColor
import app.fuelwise.core.domain.privacy.AppLockPolicy
import app.fuelwise.core.domain.trip.AutoTripPause
import app.fuelwise.core.model.Money
import app.fuelwise.core.notifications.NotificationChannels
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.locale.AppLanguage
import app.fuelwise.core.ui.privacy.DeviceAuth
import app.fuelwise.core.ui.privacy.findFragmentActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Things the screen shows that come from the device rather than preferences. */
internal data class SettingsChrome(
    /** Per-app language tag; empty = phone language. */
    val language: String = AppLanguage.SYSTEM,
    /** The phone has a screen lock, so the app lock can be turned on. */
    val appLockAvailable: Boolean = true,
    val dynamicColorSupported: Boolean = false,
    val versionName: String = "",
    val showHomeCity: Boolean = false,
    val showExport: Boolean = false,
    val showAutoTrips: Boolean = false,
)

/** Everything the screen can ask for; the route wires these to the view model, the system and navigation. */
internal data class SettingsActions(
    val onBack: () -> Unit = {},
    val onUnits: () -> Unit = {},
    val onHomeCity: () -> Unit = {},
    val onTariff: (Money?) -> Unit = {},
    val onUpi: (vpa: String, name: String) -> Unit = { _, _ -> },
    val onLanguage: (String) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    val onAppLock: (Boolean) -> Unit = {},
    val onAppLockTimeout: (Int) -> Unit = {},
    val onHideAmounts: (Boolean) -> Unit = {},
    val onWeeklyDigest: (Boolean) -> Unit = {},
    val onMonthlyRecap: (Boolean) -> Unit = {},
    val onChannel: (String) -> Unit = {},
    val onImport: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onDeleteAll: (alsoDrive: Boolean) -> Unit = {},
    val onAutoTripSetup: () -> Unit = {},
    val onPauseAutoTrips: (AutoTripPause) -> Unit = {},
    val onResumeAutoTrips: () -> Unit = {},
    val onTurnOffAutoTrips: () -> Unit = {},
)

@Composable
internal fun SettingsScreen(links: SettingsLinks, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var language by remember { mutableStateOf(AppLanguage.current(context)) }
    val chrome = SettingsChrome(
        language = language,
        appLockAvailable = remember { DeviceAuth.isAvailable(context) },
        dynamicColorSupported = supportsDynamicColor,
        versionName = remember { context.versionName() },
        showHomeCity = links.onHomeCity != null,
        showExport = links.onExport != null,
        showAutoTrips = links.onAutoTripSetup != null,
    )
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.DataDeleted -> {
                    Toast.makeText(context, resources.getString(R.string.settings_deleted_toast), Toast.LENGTH_LONG)
                        .show()
                    links.onDataDeleted()
                }

                is SettingsEvent.DriveDeleteFailed -> Toast.makeText(
                    context,
                    resources.getString(R.string.settings_delete_drive_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    SettingsContent(
        state = state,
        chrome = chrome,
        actions = SettingsActions(
            onBack = links.onBack,
            onUnits = { links.onUnits(null) },
            onHomeCity = { links.onHomeCity?.invoke() },
            onTariff = viewModel::setHomeTariff,
            onUpi = viewModel::setUpi,
            onLanguage = { tag ->
                language = tag
                AppLanguage.set(context, tag)
                // Android 13+ restarts the activity itself; older versions re-read the language on recreate.
                if (AppLanguage.needsRecreate) (context.findFragmentActivity() as Activity?)?.recreate()
            },
            onTheme = viewModel::setThemeMode,
            onDynamicColor = viewModel::setDynamicColor,
            onAppLock = { enable ->
                val activity = context.findFragmentActivity()
                if (!enable) {
                    viewModel.setAppLock(false)
                } else if (activity != null) {
                    // One successful check first, so nobody locks themselves out with a lock they can't pass.
                    DeviceAuth.prompt(activity, resources.getString(R.string.settings_app_lock_prompt_title), null) {
                        if (it) viewModel.setAppLock(true)
                    }
                }
            },
            onAppLockTimeout = viewModel::setAppLockTimeout,
            onHideAmounts = viewModel::setHideAmounts,
            onWeeklyDigest = viewModel::setWeeklyDigest,
            onMonthlyRecap = viewModel::setMonthlyRecap,
            onChannel = { channel -> context.openChannelSettings(channel) },
            onImport = links.onImport,
            onExport = { links.onExport?.invoke() },
            onBackup = links.onBackup,
            onDeleteAll = viewModel::deleteAllData,
            onAutoTripSetup = { links.onAutoTripSetup?.invoke() },
            onPauseAutoTrips = viewModel::pauseAutoTrips,
            onResumeAutoTrips = viewModel::resumeAutoTrips,
            onTurnOffAutoTrips = viewModel::turnOffAutoTrips,
        ),
    )
}

/** Which sheet or dialog is open; one at a time. */
private enum class SettingsSheet { Tariff, Upi, Language, Theme, LockTimeout, Delete, AutoTrips }

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    chrome: SettingsChrome,
    actions: SettingsActions,
    initialSheet: String? = null,
) {
    var sheet by rememberSaveable { mutableStateOf(initialSheet?.let { SettingsSheet.valueOf(it) }) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.settings_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.settings_back),
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = FwTheme.spacing.gutter)
                .padding(bottom = FwTheme.spacing.xxl),
        ) {
            GeneralSection(state, chrome, actions) { sheet = it }
            AppearanceSection(state, chrome, actions) { sheet = it }
            PrivacySection(state, chrome, actions) { sheet = it }
            NotificationsSection(state, actions)
            if (state.autoTrips != null &&
                chrome.showAutoTrips
            ) {
                LocationSection(state.autoTrips, actions) { sheet = it }
            }
            DataSection(chrome, actions) { sheet = it }
            Header(R.string.settings_section_about)
            FwSettingsRow(stringResource(R.string.settings_version), value = chrome.versionName)
        }
    }
    when (sheet) {
        SettingsSheet.Tariff -> TariffSheet(state.homeTariff, actions.onTariff) { sheet = null }

        SettingsSheet.Upi -> UpiSheet(state.upiVpa, state.upiName, actions.onUpi) { sheet = null }

        SettingsSheet.Language -> FwChoiceSheet(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.CHOICES.map { stringResource(languageLabel(it)) },
            selectedIndex = AppLanguage.CHOICES.indexOf(chrome.language).coerceAtLeast(0),
            onSelect = {
                sheet = null
                actions.onLanguage(AppLanguage.CHOICES[it])
            },
            onDismiss = { sheet = null },
        )

        SettingsSheet.Theme -> FwChoiceSheet(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { stringResource(themeLabel(it)) },
            selectedIndex = state.themeMode.ordinal,
            onSelect = {
                sheet = null
                actions.onTheme(ThemeMode.entries[it])
            },
            onDismiss = { sheet = null },
        )

        SettingsSheet.LockTimeout -> FwChoiceSheet(
            title = stringResource(R.string.settings_app_lock_after),
            options = AppLockPolicy.TIMEOUT_CHOICES.map { timeoutLabel(it) },
            selectedIndex = AppLockPolicy.TIMEOUT_CHOICES.indexOf(state.appLockTimeoutMinutes).coerceAtLeast(0),
            onSelect = {
                sheet = null
                actions.onAppLockTimeout(AppLockPolicy.TIMEOUT_CHOICES[it])
            },
            onDismiss = { sheet = null },
        )

        SettingsSheet.AutoTrips -> AutoTripsSheet(state.autoTrips, actions) { sheet = null }

        SettingsSheet.Delete -> DeleteAllDialog(
            driveConnected = state.driveConnected,
            deleting = state.deleting,
            onConfirm = actions.onDeleteAll,
            onDismiss = { sheet = null },
        )

        null -> Unit
    }
}

@Composable
private fun GeneralSection(
    state: SettingsUiState,
    chrome: SettingsChrome,
    actions: SettingsActions,
    open: (SettingsSheet) -> Unit,
) {
    Header(R.string.settings_section_general, first = true)
    val unitsSummary = unitsSummary(state.units)
    FwSettingsRow(
        stringResource(R.string.settings_units),
        value = if (state.vehicleUnitOverrides > 0) {
            pluralStringResource(
                R.plurals.settings_units_overrides,
                state.vehicleUnitOverrides,
                unitsSummary,
                state.vehicleUnitOverrides,
            )
        } else {
            unitsSummary
        },
        onClick = actions.onUnits,
    )
    FwSettingsRow(
        stringResource(R.string.settings_currency),
        value = stringResource(R.string.settings_currency_inr),
    )
    if (chrome.showHomeCity) {
        FwSettingsRow(
            stringResource(R.string.settings_home_city),
            value = stringResource(R.string.settings_home_city_value),
            onClick = actions.onHomeCity,
        )
    }
    FwSettingsRow(
        stringResource(R.string.settings_tariff),
        value = state.homeTariff?.let {
            stringResource(R.string.settings_tariff_value, IndianNumberFormat.rupees(it))
        } ?: stringResource(R.string.settings_tariff_unset),
        onClick = { open(SettingsSheet.Tariff) },
    )
    FwSettingsRow(
        stringResource(R.string.settings_upi),
        value = state.upiVpa ?: stringResource(R.string.settings_upi_unset),
        onClick = { open(SettingsSheet.Upi) },
    )
    FwSettingsRow(
        stringResource(R.string.settings_language),
        value = stringResource(languageLabel(chrome.language)),
        onClick = { open(SettingsSheet.Language) },
    )
}

@Composable
private fun AppearanceSection(
    state: SettingsUiState,
    chrome: SettingsChrome,
    actions: SettingsActions,
    open: (SettingsSheet) -> Unit,
) {
    Header(R.string.settings_section_appearance)
    FwSettingsRow(
        stringResource(R.string.settings_theme),
        value = stringResource(themeLabel(state.themeMode)),
        onClick = { open(SettingsSheet.Theme) },
    )
    if (chrome.dynamicColorSupported) {
        FwSwitchRow(
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_body),
            checked = state.dynamicColor,
            onCheckedChange = actions.onDynamicColor,
        )
    }
}

@Composable
private fun PrivacySection(
    state: SettingsUiState,
    chrome: SettingsChrome,
    actions: SettingsActions,
    open: (SettingsSheet) -> Unit,
) {
    Header(R.string.settings_section_privacy)
    FwSwitchRow(
        title = stringResource(R.string.settings_app_lock),
        subtitle = stringResource(
            if (chrome.appLockAvailable || state.appLock) {
                R.string.settings_app_lock_body
            } else {
                R.string.settings_app_lock_unavailable
            },
        ),
        checked = state.appLock,
        onCheckedChange = { if (chrome.appLockAvailable || !it) actions.onAppLock(it) },
    )
    if (state.appLock) {
        FwSettingsRow(
            stringResource(R.string.settings_app_lock_after),
            value = timeoutLabel(state.appLockTimeoutMinutes),
            onClick = { open(SettingsSheet.LockTimeout) },
        )
    }
    FwSwitchRow(
        title = stringResource(R.string.settings_hide_amounts),
        subtitle = stringResource(R.string.settings_hide_amounts_body),
        checked = state.hideAmounts,
        onCheckedChange = actions.onHideAmounts,
    )
}

@Composable
private fun NotificationsSection(state: SettingsUiState, actions: SettingsActions) {
    Header(R.string.settings_section_notifications)
    FwSwitchRow(
        title = stringResource(R.string.settings_weekly_digest),
        subtitle = stringResource(R.string.settings_weekly_digest_body),
        checked = state.weeklyDigest,
        onCheckedChange = actions.onWeeklyDigest,
    )
    FwSwitchRow(
        title = stringResource(R.string.settings_monthly_recap),
        subtitle = stringResource(R.string.settings_monthly_recap_body),
        checked = state.monthlyRecap,
        onCheckedChange = actions.onMonthlyRecap,
    )
    // Channels without an in-app switch: Android owns their on/off, sound and style.
    FwSettingsRow(
        stringResource(R.string.settings_channel_reminders),
        value = stringResource(R.string.settings_channel_system),
        onClick = { actions.onChannel(NotificationChannels.REMINDERS) },
    )
    FwSettingsRow(
        stringResource(R.string.settings_channel_price_alerts),
        value = stringResource(R.string.settings_channel_system),
        onClick = { actions.onChannel(NotificationChannels.PRICE_ALERTS) },
    )
    FwSettingsRow(
        stringResource(R.string.settings_channel_trips),
        value = stringResource(R.string.settings_channel_system),
        onClick = { actions.onChannel(NotificationChannels.TRIP_RECORDING) },
    )
    if (state.autoTrips != null) {
        FwSettingsRow(
            stringResource(R.string.settings_channel_auto_trips),
            value = stringResource(R.string.settings_channel_system),
            onClick = { actions.onChannel(NotificationChannels.AUTOMATIC_TRIPS) },
        )
    }
}

/**
 * Location & trips (design.md screen 29, PRD F-21.3). Off leads to setup; on opens pause and turn-off choices. Route
 * retention and private places arrive with F-21.6.
 */
@Composable
private fun LocationSection(auto: AutoTripsUi, actions: SettingsActions, open: (SettingsSheet) -> Unit) {
    Header(R.string.settings_section_location)
    FwSettingsRow(
        stringResource(R.string.settings_auto_trips),
        value = autoTripsValue(auto),
        onClick = { if (auto.enabled) open(SettingsSheet.AutoTrips) else actions.onAutoTripSetup() },
    )
}

@Composable
private fun autoTripsValue(auto: AutoTripsUi): String {
    val until = auto.pausedUntilUtcMs
    return when {
        !auto.enabled -> stringResource(R.string.settings_auto_trips_off)
        until == Long.MAX_VALUE -> stringResource(R.string.settings_auto_trips_paused)
        until != null -> stringResource(R.string.settings_auto_trips_paused_until, pauseEnd(until))
        auto.oneTap -> stringResource(R.string.settings_auto_trips_one_tap)
        else -> stringResource(R.string.settings_auto_trips_on)
    }
}

/** "18:00" today, otherwise "24 Oct, 05:00". */
private fun pauseEnd(utcMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(utcMs).atZone(zone)
    val today = LocalDate.now(zone)
    return if (at.toLocalDate() == today) at.format(PAUSE_TIME) else at.format(PAUSE_DAY_TIME)
}

private val PAUSE_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val PAUSE_DAY_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

/** Pause for a while, resume, or turn automatic trips off. */
@Composable
private fun AutoTripsSheet(auto: AutoTripsUi?, actions: SettingsActions, onDismiss: () -> Unit) {
    val paused = auto?.pausedUntilUtcMs != null
    val choices = buildList {
        if (paused) add(R.string.settings_auto_trips_resume to actions.onResumeAutoTrips)
        add(R.string.settings_auto_trips_pause_hour to { actions.onPauseAutoTrips(AutoTripPause.OneHour) })
        add(R.string.settings_auto_trips_pause_tomorrow to { actions.onPauseAutoTrips(AutoTripPause.UntilTomorrow) })
        add(R.string.settings_auto_trips_pause_until_on to { actions.onPauseAutoTrips(AutoTripPause.UntilTurnedOn) })
        add(R.string.settings_auto_trips_turn_off to actions.onTurnOffAutoTrips)
    }
    FwChoiceSheet(
        title = stringResource(R.string.settings_auto_trips),
        options = choices.map { stringResource(it.first) },
        selectedIndex = -1,
        onSelect = {
            onDismiss()
            choices[it].second()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun DataSection(chrome: SettingsChrome, actions: SettingsActions, open: (SettingsSheet) -> Unit) {
    Header(R.string.settings_section_data)
    FwSettingsRow(
        stringResource(R.string.settings_import),
        value = stringResource(R.string.settings_import_value),
        onClick = actions.onImport,
    )
    if (chrome.showExport) {
        FwSettingsRow(
            stringResource(R.string.settings_export),
            onClick = actions.onExport,
        )
    }
    FwSettingsRow(stringResource(R.string.settings_backup), onClick = actions.onBackup)
    FwSettingsRow(
        stringResource(R.string.settings_delete_all),
        value = stringResource(R.string.settings_delete_all_value),
        titleColor = FwTheme.colors.negative,
        onClick = { open(SettingsSheet.Delete) },
    )
}

@Composable
private fun Header(title: Int, first: Boolean = false) {
    FwSectionHeader(
        stringResource(title),
        Modifier.padding(top = if (first) FwTheme.spacing.s else FwTheme.spacing.xl, bottom = FwTheme.spacing.xxs),
    )
}

private fun languageLabel(tag: String): Int = when (tag) {
    "en" -> R.string.settings_language_en
    "hi" -> R.string.settings_language_hi
    else -> R.string.settings_language_system
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}

@Composable
private fun timeoutLabel(minutes: Int): String = if (minutes <= 0) {
    stringResource(R.string.settings_app_lock_immediately)
} else {
    pluralStringResource(R.plurals.settings_app_lock_minutes, minutes, minutes)
}

/** The app's per-channel screen in system settings (Android 8+, which is our minSdk). */
private fun Context.openChannelSettings(channel: String) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun Context.versionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

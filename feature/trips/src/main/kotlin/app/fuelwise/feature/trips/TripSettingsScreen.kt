package app.fuelwise.feature.trips

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwChoiceSheet
import app.fuelwise.core.designsystem.component.FwSectionHeader
import app.fuelwise.core.designsystem.component.FwSettingsRow
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.AutoTripPause
import app.fuelwise.core.domain.trip.RouteRetention
import app.fuelwise.core.location.rememberCarBluetoothLinker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the app may do with location right now; read from the system each time the screen resumes. */
internal enum class LocationAccess { AllTheTime, WhileUsing, Off }

/** Everything the screen can ask for; the route wires these to the view model, the system and navigation. */
internal data class TripSettingsActions(
    val onBack: () -> Unit = {},
    val onAutoTripSetup: () -> Unit = {},
    val onPauseAutoTrips: (AutoTripPause) -> Unit = {},
    val onResumeAutoTrips: () -> Unit = {},
    val onTurnOffAutoTrips: () -> Unit = {},
    val onBatterySaver: () -> Unit = {},
    val onLocationAccess: () -> Unit = {},
    /** Null when this phone can't link a car (before Android 12). */
    val onLinkCar: ((vehicleId: String) -> Unit)? = null,
    val onUnlinkCar: (vehicleId: String) -> Unit = {},
    val onRouteRetention: (RouteRetention) -> Unit = {},
    val onPrivatePlaces: () -> Unit = {},
    val onDeleteLocationHistory: () -> Unit = {},
)

@Composable
internal fun TripSettingsScreen(links: TripsLinks, viewModel: TripSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.locationAccess()) }
    // The user may change the permission in system settings and come back.
    LifecycleResumeEffect(context) {
        access = context.locationAccess()
        onPauseOrDispose {}
    }
    // The system chooser hands back only the car, so remember which vehicle asked.
    var linkingVehicle by rememberSaveable { mutableStateOf<String?>(null) }
    val launchLinker = rememberCarBluetoothLinker { link -> linkingVehicle?.let { viewModel.linkCar(it, link) } }
    TripSettingsContent(
        state = state,
        access = access,
        actions = TripSettingsActions(
            onBack = links.onBack,
            onAutoTripSetup = links.onAutoSetup,
            onPauseAutoTrips = viewModel::pauseAutoTrips,
            onResumeAutoTrips = viewModel::resumeAutoTrips,
            onTurnOffAutoTrips = viewModel::turnOffAutoTrips,
            onBatterySaver = links.onBatterySaver,
            onLocationAccess = context::openAppSettings,
            onLinkCar = launchLinker?.let { launch ->
                { vehicleId ->
                    linkingVehicle = vehicleId
                    launch()
                }
            },
            onUnlinkCar = viewModel::unlinkCar,
            onRouteRetention = viewModel::setRouteRetention,
            onPrivatePlaces = links.onPrivatePlaces,
            onDeleteLocationHistory = viewModel::deleteLocationHistory,
        ),
    )
}

/** Which sheet is open; one at a time. [CarChoices] carries the vehicle. */
private sealed interface TripSettingsSheet {
    data object AutoTrips : TripSettingsSheet

    data object Retention : TripSettingsSheet

    data object DeleteLocation : TripSettingsSheet

    data class CarChoices(val vehicleId: String) : TripSettingsSheet
}

/** Trip settings (design.md screen 29, moved from Settings into Trips on 12 Nov 2026; PRD F-21.3, F-21.6, F-21.7). */
@Composable
internal fun TripSettingsContent(state: TripSettingsUiState, access: LocationAccess, actions: TripSettingsActions) {
    var sheet by remember { mutableStateOf<TripSettingsSheet?>(null) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.trip_settings_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.trips_back),
        )
        if (state.loading) return@Column
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = FwTheme.spacing.gutter)
                .padding(bottom = FwTheme.spacing.xxl),
        ) {
            AutoTripsSection(state, access, actions) { sheet = it }
            CarSection(state, actions) { sheet = it }
            PrivacySection(state, actions) { sheet = it }
        }
    }
    TripSettingsSheets(sheet, state, actions) { sheet = null }
}

@Composable
private fun AutoTripsSection(
    state: TripSettingsUiState,
    access: LocationAccess,
    actions: TripSettingsActions,
    open: (TripSettingsSheet) -> Unit,
) {
    Header(R.string.trip_settings_section_auto, first = true)
    val auto = state.autoTrips
    if (auto != null) {
        FwSettingsRow(
            stringResource(R.string.trip_settings_auto_trips),
            value = autoTripsValue(auto),
            onClick = { if (auto.enabled) open(TripSettingsSheet.AutoTrips) else actions.onAutoTripSetup() },
        )
    }
    // GPS: what the system allows, for manual and automatic trips alike. Changed in system settings only.
    FwSettingsRow(
        stringResource(R.string.trip_settings_location_access),
        value = stringResource(
            when (access) {
                LocationAccess.AllTheTime -> R.string.trip_settings_location_all_the_time
                LocationAccess.WhileUsing -> R.string.trip_settings_location_while_using
                LocationAccess.Off -> R.string.trip_settings_location_off
            },
        ),
        onClick = actions.onLocationAccess,
    )
    if (auto?.enabled == true) {
        FwSettingsRow(
            stringResource(R.string.trip_settings_battery_saver),
            value = stringResource(R.string.trip_settings_battery_saver_value),
            onClick = actions.onBatterySaver,
        )
    }
}

/** Car Bluetooth per vehicle (PRD F-21.3, design.md screen 22): connecting starts a trip for that vehicle. */
@Composable
private fun CarSection(state: TripSettingsUiState, actions: TripSettingsActions, open: (TripSettingsSheet) -> Unit) {
    val link = actions.onLinkCar ?: return
    if (state.autoTrips?.enabled != true || state.cars.isEmpty()) return
    Header(R.string.trip_settings_section_car)
    state.cars.forEach { car ->
        FwSettingsRow(
            car.vehicleName,
            value = when {
                !car.linked -> stringResource(R.string.trip_settings_car_none)
                else -> car.carName ?: stringResource(R.string.trip_settings_car_linked)
            },
            onClick = { if (car.linked) open(TripSettingsSheet.CarChoices(car.vehicleId)) else link(car.vehicleId) },
        )
    }
}

/** PRD F-21.6: how long routes stay, places trimmed from route ends, and a separate delete. */
@Composable
private fun PrivacySection(
    state: TripSettingsUiState,
    actions: TripSettingsActions,
    open: (TripSettingsSheet) -> Unit,
) {
    Header(R.string.trip_settings_section_privacy)
    FwSettingsRow(
        stringResource(R.string.trip_settings_route_retention),
        value = stringResource(retentionLabel(state.routeRetention)),
        onClick = { open(TripSettingsSheet.Retention) },
    )
    FwSettingsRow(
        stringResource(R.string.trip_settings_private_places),
        value = if (state.privatePlaceCount == 0) {
            stringResource(R.string.trip_settings_private_places_none)
        } else {
            pluralStringResource(
                R.plurals.trip_settings_private_places_count,
                state.privatePlaceCount,
                state.privatePlaceCount,
            )
        },
        onClick = actions.onPrivatePlaces,
    )
    FwSettingsRow(
        stringResource(R.string.trip_settings_delete_location),
        titleColor = FwTheme.colors.negative,
        onClick = { open(TripSettingsSheet.DeleteLocation) },
    )
}

@Composable
private fun TripSettingsSheets(
    sheet: TripSettingsSheet?,
    state: TripSettingsUiState,
    actions: TripSettingsActions,
    close: () -> Unit,
) {
    when (sheet) {
        TripSettingsSheet.AutoTrips -> AutoTripsSheet(state.autoTrips, actions, close)

        TripSettingsSheet.Retention -> FwChoiceSheet(
            title = stringResource(R.string.trip_settings_route_retention),
            options = RouteRetention.entries.map { stringResource(retentionLabel(it)) },
            selectedIndex = state.routeRetention.ordinal,
            onSelect = {
                close()
                actions.onRouteRetention(RouteRetention.entries[it])
            },
            onDismiss = close,
            subtitle = stringResource(R.string.trip_settings_route_retention_body),
        )

        TripSettingsSheet.DeleteLocation -> FwChoiceSheet(
            title = stringResource(R.string.trip_settings_delete_location_title),
            options = listOf(stringResource(R.string.trip_settings_delete_location_confirm)),
            selectedIndex = -1,
            onSelect = {
                close()
                actions.onDeleteLocationHistory()
            },
            onDismiss = close,
            subtitle = stringResource(R.string.trip_settings_delete_location_body),
        )

        is TripSettingsSheet.CarChoices -> {
            val options = listOf(
                R.string.trip_settings_car_change to { actions.onLinkCar?.invoke(sheet.vehicleId) },
                R.string.trip_settings_car_unlink to { actions.onUnlinkCar(sheet.vehicleId) },
            )
            FwChoiceSheet(
                title = stringResource(R.string.trip_settings_section_car),
                options = options.map { stringResource(it.first) },
                selectedIndex = -1,
                onSelect = {
                    close()
                    options[it].second()
                },
                onDismiss = close,
                subtitle = stringResource(R.string.trip_settings_car_hint),
            )
        }

        null -> Unit
    }
}

/** Pause for a while, resume, or turn automatic trips off. */
@Composable
private fun AutoTripsSheet(auto: AutoTripsUi?, actions: TripSettingsActions, onDismiss: () -> Unit) {
    val paused = auto?.pausedUntilUtcMs != null
    val choices = buildList {
        if (paused) add(R.string.trip_settings_auto_trips_resume to actions.onResumeAutoTrips)
        add(R.string.trip_settings_auto_trips_pause_hour to { actions.onPauseAutoTrips(AutoTripPause.OneHour) })
        add(
            R.string.trip_settings_auto_trips_pause_tomorrow to
                { actions.onPauseAutoTrips(AutoTripPause.UntilTomorrow) },
        )
        add(
            R.string.trip_settings_auto_trips_pause_until_on to
                { actions.onPauseAutoTrips(AutoTripPause.UntilTurnedOn) },
        )
        add(R.string.trip_settings_auto_trips_turn_off to actions.onTurnOffAutoTrips)
    }
    FwChoiceSheet(
        title = stringResource(R.string.trip_settings_auto_trips),
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
private fun Header(title: Int, first: Boolean = false) {
    FwSectionHeader(
        stringResource(title),
        Modifier.padding(top = if (first) FwTheme.spacing.s else FwTheme.spacing.xl, bottom = FwTheme.spacing.xxs),
    )
}

private fun retentionLabel(retention: RouteRetention): Int = when (retention) {
    RouteRetention.Days30 -> R.string.trip_settings_route_retention_30
    RouteRetention.Days90 -> R.string.trip_settings_route_retention_90
    RouteRetention.Year -> R.string.trip_settings_route_retention_year
    RouteRetention.Forever -> R.string.trip_settings_route_retention_forever
}

@Composable
private fun autoTripsValue(auto: AutoTripsUi): String {
    val until = auto.pausedUntilUtcMs
    return when {
        !auto.enabled -> stringResource(R.string.trip_settings_auto_trips_off)
        until == Long.MAX_VALUE -> stringResource(R.string.trip_settings_auto_trips_paused)
        until != null -> stringResource(R.string.trip_settings_auto_trips_paused_until, pauseEnd(until))
        auto.oneTap -> stringResource(R.string.trip_settings_auto_trips_one_tap)
        else -> stringResource(R.string.trip_settings_auto_trips_on)
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

private fun Context.locationAccess(): LocationAccess = when {
    !granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION) ->
        LocationAccess.Off

    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(BACKGROUND_LOCATION) -> LocationAccess.AllTheTime

    else -> LocationAccess.WhileUsing
}

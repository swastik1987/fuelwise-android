package app.fuelwise.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.BackupSettingsRepository
import app.fuelwise.core.data.repository.LocalDataReset
import app.fuelwise.core.data.repository.ThemeMode
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.BackupError
import app.fuelwise.core.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val units: DisplayUnits = DisplayUnits.INDIA,
    /** How many vehicles use their own units instead of the default. */
    val vehicleUnitOverrides: Int = 0,
    val homeTariff: Money? = null,
    val upiVpa: String? = null,
    val upiName: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val appLock: Boolean = false,
    val appLockTimeoutMinutes: Int = 0,
    val hideAmounts: Boolean = false,
    val weeklyDigest: Boolean = false,
    val monthlyRecap: Boolean = true,
    /** Drive is connected, so "also delete Drive backups" is offered. */
    val driveConnected: Boolean = false,
    val deleting: Boolean = false,
)

sealed interface SettingsEvent {
    /** Everything is gone; the app starts over at onboarding. */
    data object DataDeleted : SettingsEvent

    /** Drive backups couldn't be deleted, so nothing was deleted (the user can retry or untick Drive). */
    data class DriveDeleteFailed(val error: BackupError) : SettingsEvent
}

/** Settings (PRD F-18, design.md screen 29). Orchestration only: values live in preferences, resets in core:data. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userData: UserDataRepository,
    vehicles: VehicleRepository,
    backupSettings: BackupSettingsRepository,
    private val backups: BackupEraser,
    private val reset: LocalDataReset,
) : ViewModel() {
    private val deleting = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        userData.userData,
        vehicles.observeVehicles(),
        backupSettings.settings,
        deleting,
    ) { data, list, backup, isDeleting ->
        val ids = list.map { it.id }.toSet()
        SettingsUiState(
            loading = false,
            units = data.units.default,
            vehicleUnitOverrides = data.units.perVehicle.keys.count { it in ids },
            homeTariff = data.homeTariff,
            upiVpa = data.upiVpa,
            upiName = data.upiName,
            themeMode = data.themeMode,
            dynamicColor = data.dynamicColor,
            appLock = data.appLock,
            appLockTimeoutMinutes = data.appLockTimeoutMinutes,
            hideAmounts = data.hideAmounts,
            weeklyDigest = data.weeklyDigest,
            monthlyRecap = data.monthlyRecap,
            driveConnected = backup.accountEmail != null,
            deleting = isDeleting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun setThemeMode(mode: ThemeMode) = launch { userData.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launch { userData.setDynamicColor(enabled) }

    /** Turning the lock on is confirmed with one successful prompt first (the screen does that). */
    fun setAppLock(enabled: Boolean) = launch { userData.setAppLock(enabled, state.value.appLockTimeoutMinutes) }

    fun setAppLockTimeout(minutes: Int) = launch { userData.setAppLock(state.value.appLock, minutes) }

    fun setHideAmounts(hide: Boolean) = launch { userData.setHideAmounts(hide) }

    fun setWeeklyDigest(on: Boolean) = launch { userData.setDigestChoices(on, state.value.monthlyRecap) }

    fun setMonthlyRecap(on: Boolean) = launch { userData.setDigestChoices(state.value.weeklyDigest, on) }

    /** A null or zero tariff clears it, so the charge sheet falls back to the last price paid. */
    fun setHomeTariff(tariff: Money?) = launch { userData.setHomeTariff(tariff?.takeIf { it.paise > 0 }) }

    fun setUpi(vpa: String, name: String) = launch { userData.setUpiPayee(vpa, name) }

    /**
     * "Delete all data" (PRD F-18). With [alsoDrive], Drive goes first: if it can't be reached nothing is deleted,
     * so the user never ends up with an empty phone and a backup they meant to remove.
     */
    fun deleteAllData(alsoDrive: Boolean) {
        if (deleting.value) return
        deleting.value = true
        viewModelScope.launch {
            try {
                if (alsoDrive) {
                    backups.deleteDriveBackups()?.let { error ->
                        eventChannel.send(SettingsEvent.DriveDeleteFailed(error))
                        return@launch
                    }
                }
                backups.forgetKey()
                reset.deleteEverything()
                eventChannel.send(SettingsEvent.DataDeleted)
            } finally {
                deleting.update { false }
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

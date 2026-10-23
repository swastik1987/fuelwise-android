package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.FeatureFlags
import app.fuelwise.core.data.repository.ThemeMode
import app.fuelwise.core.data.repository.UserData
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory [UserDataRepository] starting from first-run defaults. */
class FakeUserDataRepository(initial: UserData = DEFAULT) : UserDataRepository {
    private val state = MutableStateFlow(initial)
    override val userData: StateFlow<UserData> = state

    override suspend fun setOnboardingCompleted(completed: Boolean) = state.update {
        it.copy(onboardingCompleted = completed)
    }

    override suspend fun setSelectedVehicle(vehicleId: String?) = state.update {
        it.copy(selectedVehicleId = vehicleId)
    }

    override suspend fun setHomeCity(cityId: String?) = state.update { it.copy(homeCityId = cityId) }

    override suspend fun setDigestChoices(weeklyDigest: Boolean, monthlyRecap: Boolean) =
        state.update { it.copy(weeklyDigest = weeklyDigest, monthlyRecap = monthlyRecap) }

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setHideAmounts(hide: Boolean) = state.update { it.copy(hideAmounts = hide) }

    override suspend fun markMilestoneShown(key: String) = state.update {
        it.copy(
            shownMilestones =
                it.shownMilestones + key,
        )
    }

    override suspend fun setUpiPayee(vpa: String, name: String) = state.update {
        it.copy(upiVpa = vpa.trim(), upiName = name.trim())
    }

    override suspend fun setUnits(vehicleId: String?, units: DisplayUnits?) = state.update {
        val current = it.units
        it.copy(
            units = when {
                vehicleId == null -> current.copy(default = units ?: DisplayUnits.INDIA)
                units == null -> current.copy(perVehicle = current.perVehicle - vehicleId)
                else -> current.copy(perVehicle = current.perVehicle + (vehicleId to units))
            },
        )
    }

    override suspend fun setHomeTariff(tariff: Money?) = state.update { it.copy(homeTariff = tariff) }

    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }

    override suspend fun setAppLock(enabled: Boolean, timeoutMinutes: Int) = state.update {
        it.copy(appLock = enabled, appLockTimeoutMinutes = timeoutMinutes)
    }

    companion object {
        val DEFAULT = UserData(
            onboardingCompleted = false,
            selectedVehicleId = null,
            homeCityId = null,
            weeklyDigest = false,
            monthlyRecap = true,
            themeMode = ThemeMode.System,
            hideAmounts = false,
            flags = FeatureFlags(automaticTrips = true, multiDeviceSync = false, tripPlanner = false),
        )
    }
}

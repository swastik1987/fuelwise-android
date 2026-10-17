package app.fuelwise.core.data.repository

import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.ThemeMode as ProtoThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App settings and first-run choices, as the UI sees them. */
data class UserData(
    val onboardingCompleted: Boolean,
    val selectedVehicleId: String?,
    val homeCityId: String?,
    val weeklyDigest: Boolean,
    val monthlyRecap: Boolean,
    val themeMode: ThemeMode,
    val hideAmounts: Boolean,
    val flags: FeatureFlags,
    /** Milestone keys already shown (PRD F-17). */
    val shownMilestones: Set<String> = emptySet(),
)

enum class ThemeMode { System, Light, Dark }

/** R2/R3 features stay off until their release (CLAUDE.md feature flags). */
data class FeatureFlags(val automaticTrips: Boolean, val multiDeviceSync: Boolean, val tripPlanner: Boolean)

interface UserDataRepository {
    val userData: Flow<UserData>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setSelectedVehicle(vehicleId: String?)

    suspend fun setHomeCity(cityId: String?)

    suspend fun setDigestChoices(weeklyDigest: Boolean, monthlyRecap: Boolean)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setHideAmounts(hide: Boolean)

    suspend fun markMilestoneShown(key: String)
}

internal class DataStoreUserDataRepository @Inject constructor(private val source: PreferencesDataSource) :
    UserDataRepository {
    override val userData: Flow<UserData> = source.preferences.map { p ->
        UserData(
            onboardingCompleted = p.onboardingCompleted,
            selectedVehicleId = p.selectedVehicleId.ifEmpty { null },
            homeCityId = p.homeCityId.ifEmpty { null },
            weeklyDigest = p.weeklyDigest,
            monthlyRecap = p.monthlyRecap,
            themeMode = when (p.themeMode) {
                ProtoThemeMode.THEME_MODE_LIGHT -> ThemeMode.Light
                ProtoThemeMode.THEME_MODE_DARK -> ThemeMode.Dark
                else -> ThemeMode.System
            },
            hideAmounts = p.hideAmounts,
            flags = FeatureFlags(
                automaticTrips = p.featureFlags.automaticTrips,
                multiDeviceSync = p.featureFlags.multiDeviceSync,
                tripPlanner = p.featureFlags.tripPlanner,
            ),
            shownMilestones = p.shownMilestonesList.toSet(),
        )
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) = source.update { onboardingCompleted = completed }

    override suspend fun setSelectedVehicle(vehicleId: String?) = source.update {
        selectedVehicleId =
            vehicleId.orEmpty()
    }

    override suspend fun setHomeCity(cityId: String?) = source.update { homeCityId = cityId.orEmpty() }

    override suspend fun setDigestChoices(weeklyDigest: Boolean, monthlyRecap: Boolean) = source.update {
        this.weeklyDigest = weeklyDigest
        this.monthlyRecap = monthlyRecap
    }

    override suspend fun setThemeMode(mode: ThemeMode) = source.update {
        themeMode = when (mode) {
            ThemeMode.System -> ProtoThemeMode.THEME_MODE_SYSTEM
            ThemeMode.Light -> ProtoThemeMode.THEME_MODE_LIGHT
            ThemeMode.Dark -> ProtoThemeMode.THEME_MODE_DARK
        }
    }

    override suspend fun setHideAmounts(hide: Boolean) = source.update { hideAmounts = hide }

    override suspend fun markMilestoneShown(key: String) = source.update {
        if (key !in shownMilestones) shownMilestones.add(key)
    }
}

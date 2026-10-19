package app.fuelwise.core.data.repository

import app.fuelwise.core.datastore.DistanceUnitPref
import app.fuelwise.core.datastore.EfficiencyUnitPref
import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.ThemeMode as ProtoThemeMode
import app.fuelwise.core.datastore.UnitPreferences
import app.fuelwise.core.datastore.VolumeUnitPref
import app.fuelwise.core.datastore.unitPreferences
import app.fuelwise.core.domain.units.DisplayUnits
import app.fuelwise.core.domain.units.DistanceUnit
import app.fuelwise.core.domain.units.EfficiencyUnit
import app.fuelwise.core.domain.units.UnitSettings
import app.fuelwise.core.domain.units.VolumeUnit
import app.fuelwise.core.model.Money
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
    /** For trip-split UPI links (PRD F-07.4). Null until typed once. */
    val upiVpa: String? = null,
    val upiName: String? = null,
    /** Display units, app-wide and per vehicle (PRD F-18). */
    val units: UnitSettings = UnitSettings(),
    /** Home charging tariff per kWh (PRD F-02.5). Null until set. */
    val homeTariff: Money? = null,
    /** Material You colours (PRD F-18); off by default. */
    val dynamicColor: Boolean = false,
    /** App lock (PRD F-18) and how long the app may stay in the background before asking again. */
    val appLock: Boolean = false,
    val appLockTimeoutMinutes: Int = 0,
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

    suspend fun setUpiPayee(vpa: String, name: String)

    /** The app-wide units when [vehicleId] is null; otherwise that vehicle's override, cleared by a null [units]. */
    suspend fun setUnits(vehicleId: String?, units: DisplayUnits?)

    suspend fun setHomeTariff(tariff: Money?)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setAppLock(enabled: Boolean, timeoutMinutes: Int)
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
            upiVpa = p.upiVpa.ifEmpty { null },
            upiName = p.upiName.ifEmpty { null },
            units = UnitSettings(
                default = p.units.toDisplayUnits(),
                perVehicle = p.vehicleUnitsMap.mapValues { it.value.toDisplayUnits() },
            ),
            homeTariff = p.homeTariffPaise.takeIf { it > 0 }?.let(::Money),
            dynamicColor = p.dynamicColor,
            appLock = p.appLock,
            appLockTimeoutMinutes = p.appLockTimeoutMinutes,
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

    override suspend fun setUpiPayee(vpa: String, name: String) = source.update {
        upiVpa = vpa.trim()
        upiName = name.trim()
    }

    override suspend fun setUnits(vehicleId: String?, units: DisplayUnits?) = source.update {
        when {
            vehicleId == null -> this.units = (units ?: DisplayUnits.INDIA).toProto()
            units == null -> vehicleUnits.remove(vehicleId)
            else -> vehicleUnits.put(vehicleId, units.toProto())
        }
    }

    override suspend fun setHomeTariff(tariff: Money?) = source.update {
        homeTariffPaise = tariff?.paise?.coerceAtLeast(0) ?: 0
    }

    override suspend fun setDynamicColor(enabled: Boolean) = source.update { dynamicColor = enabled }

    override suspend fun setAppLock(enabled: Boolean, timeoutMinutes: Int) = source.update {
        appLock = enabled
        appLockTimeoutMinutes = timeoutMinutes.coerceAtLeast(0)
    }
}

internal fun UnitPreferences.toDisplayUnits() = DisplayUnits(
    distance = when (distance) {
        DistanceUnitPref.DISTANCE_UNIT_MILE -> DistanceUnit.MILE
        else -> DistanceUnit.KILOMETRE
    },
    volume = when (volume) {
        VolumeUnitPref.VOLUME_UNIT_US_GALLON -> VolumeUnit.US_GALLON
        VolumeUnitPref.VOLUME_UNIT_UK_GALLON -> VolumeUnit.UK_GALLON
        else -> VolumeUnit.LITRE
    },
    efficiency = when (efficiency) {
        EfficiencyUnitPref.EFFICIENCY_UNIT_QUANTITY_PER_100_DISTANCE -> EfficiencyUnit.QUANTITY_PER_100_DISTANCE
        EfficiencyUnitPref.EFFICIENCY_UNIT_MPG_US -> EfficiencyUnit.MPG_US
        EfficiencyUnitPref.EFFICIENCY_UNIT_MPG_UK -> EfficiencyUnit.MPG_UK
        else -> EfficiencyUnit.DISTANCE_PER_QUANTITY
    },
)

internal fun DisplayUnits.toProto(): UnitPreferences {
    val units = this
    return unitPreferences {
        distance = when (units.distance) {
            DistanceUnit.KILOMETRE -> DistanceUnitPref.DISTANCE_UNIT_KM
            DistanceUnit.MILE -> DistanceUnitPref.DISTANCE_UNIT_MILE
        }
        volume = when (units.volume) {
            VolumeUnit.LITRE -> VolumeUnitPref.VOLUME_UNIT_LITRE
            VolumeUnit.US_GALLON -> VolumeUnitPref.VOLUME_UNIT_US_GALLON
            VolumeUnit.UK_GALLON -> VolumeUnitPref.VOLUME_UNIT_UK_GALLON
        }
        efficiency = when (units.efficiency) {
            EfficiencyUnit.DISTANCE_PER_QUANTITY -> EfficiencyUnitPref.EFFICIENCY_UNIT_DISTANCE_PER_QUANTITY
            EfficiencyUnit.QUANTITY_PER_100_DISTANCE -> EfficiencyUnitPref.EFFICIENCY_UNIT_QUANTITY_PER_100_DISTANCE
            EfficiencyUnit.MPG_US -> EfficiencyUnitPref.EFFICIENCY_UNIT_MPG_US
            EfficiencyUnit.MPG_UK -> EfficiencyUnitPref.EFFICIENCY_UNIT_MPG_UK
        }
    }
}

package app.fuelwise.core.data.repository

import app.fuelwise.core.datastore.BackupError as ProtoError
import app.fuelwise.core.datastore.BackupFrequency as ProtoFrequency
import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.datastore.DistanceUnitPref
import app.fuelwise.core.datastore.EfficiencyUnitPref
import app.fuelwise.core.datastore.ThemeMode as ProtoThemeMode
import app.fuelwise.core.datastore.UnitPreferences
import app.fuelwise.core.datastore.VolumeUnitPref
import app.fuelwise.core.datastore.unitPreferences
import app.fuelwise.core.model.BackupError
import app.fuelwise.core.model.BackupFrequency
import app.fuelwise.core.model.BackupSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Backup settings and status (PRD F-14.3, F-14.8), plus the preferences a snapshot carries. */
interface BackupSettingsRepository {
    val settings: Flow<BackupSettings>

    suspend fun update(transform: (BackupSettings) -> BackupSettings)

    /** User preferences that travel in a snapshot: not the device id, not this device's backup state. */
    suspend fun exportPreferences(): Map<String, Any>

    suspend fun importPreferences(values: Map<String, Any?>)
}

internal class DataStoreBackupSettingsRepository @Inject constructor(private val source: PreferencesDataSource) :
    BackupSettingsRepository {
    override val settings: Flow<BackupSettings> = source.preferences.map { p ->
        val b = p.backup
        BackupSettings(
            frequency = when (b.frequency) {
                ProtoFrequency.BACKUP_FREQUENCY_AFTER_CHANGES -> BackupFrequency.AfterChanges
                ProtoFrequency.BACKUP_FREQUENCY_WEEKLY -> BackupFrequency.Weekly
                ProtoFrequency.BACKUP_FREQUENCY_MANUAL -> BackupFrequency.Manual
                else -> BackupFrequency.Daily
            },
            wifiOnly = b.wifiOnly,
            chargingOnly = b.chargingOnly,
            encrypted = b.encrypted,
            accountEmail = b.accountEmail.ifEmpty { null },
            lastSuccessUtcMs = b.lastSuccessAt.takeIf { it > 0 },
            lastSizeBytes = b.lastSizeBytes.takeIf { it > 0 },
            lastError = when (b.lastError) {
                ProtoError.BACKUP_ERROR_SIGN_IN -> BackupError.SignIn
                ProtoError.BACKUP_ERROR_OFFLINE -> BackupError.Offline
                ProtoError.BACKUP_ERROR_STORAGE_FULL -> BackupError.StorageFull
                ProtoError.BACKUP_ERROR_KEY_MISSING -> BackupError.KeyMissing
                ProtoError.BACKUP_ERROR_UNKNOWN -> BackupError.Unknown
                else -> null
            },
            lastAttemptUtcMs = b.lastAttemptAt.takeIf { it > 0 },
        )
    }

    override suspend fun update(transform: (BackupSettings) -> BackupSettings) {
        val next = transform(settings.first())
        source.update {
            backup = backup.toBuilder()
                .setFrequency(
                    when (next.frequency) {
                        BackupFrequency.AfterChanges -> ProtoFrequency.BACKUP_FREQUENCY_AFTER_CHANGES
                        BackupFrequency.Daily -> ProtoFrequency.BACKUP_FREQUENCY_DAILY
                        BackupFrequency.Weekly -> ProtoFrequency.BACKUP_FREQUENCY_WEEKLY
                        BackupFrequency.Manual -> ProtoFrequency.BACKUP_FREQUENCY_MANUAL
                    },
                )
                .setWifiOnly(next.wifiOnly)
                .setChargingOnly(next.chargingOnly)
                .setEncrypted(next.encrypted)
                .setAccountEmail(next.accountEmail.orEmpty())
                .setLastSuccessAt(next.lastSuccessUtcMs ?: 0)
                .setLastSizeBytes(next.lastSizeBytes ?: 0)
                .setLastError(
                    when (next.lastError) {
                        null -> ProtoError.BACKUP_ERROR_NONE
                        BackupError.SignIn -> ProtoError.BACKUP_ERROR_SIGN_IN
                        BackupError.Offline -> ProtoError.BACKUP_ERROR_OFFLINE
                        BackupError.StorageFull -> ProtoError.BACKUP_ERROR_STORAGE_FULL
                        BackupError.KeyMissing -> ProtoError.BACKUP_ERROR_KEY_MISSING
                        BackupError.Unknown -> ProtoError.BACKUP_ERROR_UNKNOWN
                    },
                )
                .setLastAttemptAt(next.lastAttemptUtcMs ?: 0)
                .build()
        }
    }

    override suspend fun exportPreferences(): Map<String, Any> {
        val p = source.preferences.first()
        return mapOf(
            KEY_SELECTED_VEHICLE to p.selectedVehicleId,
            KEY_HOME_CITY to p.homeCityId,
            KEY_WEEKLY_DIGEST to p.weeklyDigest,
            KEY_MONTHLY_RECAP to p.monthlyRecap,
            KEY_THEME to p.themeMode.name,
            KEY_HIDE_AMOUNTS to p.hideAmounts,
            KEY_MILESTONES to p.shownMilestonesList.toList(),
            KEY_UPI_VPA to p.upiVpa,
            KEY_UPI_NAME to p.upiName,
            // Settings (PRD F-18). The app lock is this device's choice and stays out of backups.
            KEY_UNITS to p.units.encode(),
            KEY_VEHICLE_UNITS to p.vehicleUnitsMap.map { (id, units) -> "$id=${units.encode()}" },
            KEY_HOME_TARIFF to p.homeTariffPaise,
            KEY_DYNAMIC_COLOR to p.dynamicColor,
        )
    }

    override suspend fun importPreferences(values: Map<String, Any?>) = source.update {
        // A restored install has data, so it skips onboarding; unknown or missing keys keep their current value.
        onboardingCompleted = true
        (values[KEY_SELECTED_VEHICLE] as? String)?.let { selectedVehicleId = it }
        (values[KEY_HOME_CITY] as? String)?.let { homeCityId = it }
        (values[KEY_WEEKLY_DIGEST] as? Boolean)?.let { weeklyDigest = it }
        (values[KEY_MONTHLY_RECAP] as? Boolean)?.let { monthlyRecap = it }
        (values[KEY_THEME] as? String)?.let { name ->
            ProtoThemeMode.entries.firstOrNull { it.name == name }?.let {
                themeMode =
                    it
            }
        }
        (values[KEY_HIDE_AMOUNTS] as? Boolean)?.let { hideAmounts = it }
        (values[KEY_MILESTONES] as? List<*>)?.filterIsInstance<String>()?.let {
            shownMilestones.clear()
            shownMilestones.addAll(it)
        }
        (values[KEY_UPI_VPA] as? String)?.let { upiVpa = it }
        (values[KEY_UPI_NAME] as? String)?.let { upiName = it }
        (values[KEY_UNITS] as? String)?.let { units = decodeUnits(it) }
        (values[KEY_VEHICLE_UNITS] as? List<*>)?.filterIsInstance<String>()?.let { list ->
            vehicleUnits.clear()
            list.forEach { entry ->
                val id = entry.substringBefore('=')
                if (id.isNotEmpty()) vehicleUnits.put(id, decodeUnits(entry.substringAfter('=')))
            }
        }
        (values[KEY_HOME_TARIFF] as? Long)?.let { homeTariffPaise = it.coerceAtLeast(0) }
        (values[KEY_DYNAMIC_COLOR] as? Boolean)?.let { dynamicColor = it }
    }

    /** "DISTANCE_UNIT_KM,VOLUME_UNIT_LITRE,EFFICIENCY_UNIT_…": snapshots carry strings, lists and numbers only. */
    private fun UnitPreferences.encode(): String = listOf(distance.name, volume.name, efficiency.name).joinToString(",")

    private fun decodeUnits(text: String): UnitPreferences {
        val names = text.split(',').toSet()
        return unitPreferences {
            DistanceUnitPref.entries.firstOrNull { it.name in names && it != DistanceUnitPref.UNRECOGNIZED }
                ?.let { distance = it }
            VolumeUnitPref.entries.firstOrNull { it.name in names && it != VolumeUnitPref.UNRECOGNIZED }
                ?.let { volume = it }
            EfficiencyUnitPref.entries.firstOrNull { it.name in names && it != EfficiencyUnitPref.UNRECOGNIZED }
                ?.let { efficiency = it }
        }
    }

    private companion object {
        const val KEY_SELECTED_VEHICLE = "selectedVehicleId"
        const val KEY_HOME_CITY = "homeCityId"
        const val KEY_WEEKLY_DIGEST = "weeklyDigest"
        const val KEY_MONTHLY_RECAP = "monthlyRecap"
        const val KEY_THEME = "themeMode"
        const val KEY_HIDE_AMOUNTS = "hideAmounts"
        const val KEY_MILESTONES = "shownMilestones"
        const val KEY_UPI_VPA = "upiVpa"
        const val KEY_UPI_NAME = "upiName"
        const val KEY_UNITS = "units"
        const val KEY_VEHICLE_UNITS = "vehicleUnits"
        const val KEY_HOME_TARIFF = "homeTariffPaise"
        const val KEY_DYNAMIC_COLOR = "dynamicColor"
    }
}

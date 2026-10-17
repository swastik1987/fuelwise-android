package app.fuelwise.core.model

/** How often Drive backup runs (PRD F-14.3). */
enum class BackupFrequency { AfterChanges, Daily, Weekly, Manual }

/** Why the last backup failed, each with its own fix on the status screen (PRD F-14.8). */
enum class BackupError { SignIn, Offline, StorageFull, KeyMissing, Unknown }

data class BackupSettings(
    val frequency: BackupFrequency = BackupFrequency.Daily,
    val wifiOnly: Boolean = false,
    val chargingOnly: Boolean = false,
    val encrypted: Boolean = false,
    val accountEmail: String? = null,
    val lastSuccessUtcMs: Long? = null,
    val lastSizeBytes: Long? = null,
    val lastError: BackupError? = null,
    val lastAttemptUtcMs: Long? = null,
) {
    val isSignedIn: Boolean get() = accountEmail != null
}

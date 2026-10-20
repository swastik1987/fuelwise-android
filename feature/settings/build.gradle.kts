plugins {
    alias(libs.plugins.fuelwise.android.feature)
}

dependencies {
    // "Delete all data" can also delete Drive backups (PRD F-18) through the backup manager.
    implementation(projects.sync.drive)
    // Channel ids for the per-channel system notification settings.
    implementation(projects.core.notifications)
}

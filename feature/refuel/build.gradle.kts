plugins {
    alias(libs.plugins.fuelwise.android.feature)
}

dependencies {
    // A single location fix for station auto-detect (PRD F-21.1). Permission already declared there.
    implementation(projects.core.location)
}

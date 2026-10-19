plugins {
    alias(libs.plugins.fuelwise.android.library)
    alias(libs.plugins.fuelwise.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    // Map display only (PRD D8; approved 19 Oct 2026, PRD section 9). No Places, no geocoding.
    implementation(libs.maps.compose)
}

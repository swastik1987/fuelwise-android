plugins {
    alias(libs.plugins.fuelwise.android.library)
    alias(libs.plugins.fuelwise.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    // Display-unit conversions (PRD F-18) live in core:domain; formatting them is this module's job.
    implementation(projects.core.domain)
    // App lock (PRD F-18; approved 19 Oct 2026, PRD section 9).
    api(libs.androidx.biometric)
    // Map display only (PRD D8; approved 19 Oct 2026, PRD section 9). No Places, no geocoding.
    implementation(libs.maps.compose)
}

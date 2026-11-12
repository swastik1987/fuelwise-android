plugins {
    alias(libs.plugins.fuelwise.android.library)
    alias(libs.plugins.fuelwise.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    // Display helpers for domain results: display units (PRD F-18) and the fair-price line (PRD F-09.3).
    implementation(projects.core.domain)
    // App lock (PRD F-18; approved 19 Oct 2026, PRD section 9).
    api(libs.androidx.biometric)
    // Activity Result API for the Android 8.x confirm-credential fallback (biometric only brings activity 1.1).
    implementation(libs.androidx.activity.compose)
    // Map display only (PRD D8; approved 19 Oct 2026, PRD section 9). No Places, no geocoding.
    implementation(libs.maps.compose)
    // Heatmap tiles for the consolidated trips map (android-maps-utils; approved 12 Nov 2026, PRD section 9).
    implementation(libs.maps.compose.utils)
}

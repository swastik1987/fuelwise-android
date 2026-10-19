plugins {
    alias(libs.plugins.fuelwise.android.library)
    // For the design-system icons and formatters (Compose BOM), as in core:notifications.
    alias(libs.plugins.fuelwise.android.compose)
    alias(libs.plugins.fuelwise.hilt)
}

dependencies {
    api(projects.core.data)
    implementation(projects.core.notifications)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // Fused Location Provider for GPS trips (PRD F-07.2; approved 19 Oct 2026, PRD §9).
    implementation(libs.play.services.location)

    testImplementation(projects.core.testing)
}

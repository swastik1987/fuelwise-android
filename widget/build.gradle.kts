plugins {
    alias(libs.plugins.fuelwise.android.library)
    alias(libs.plugins.fuelwise.android.compose)
    alias(libs.plugins.fuelwise.hilt)
    alias(libs.plugins.roborazzi)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.notifications)
    implementation(projects.core.location)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // Home-screen widgets (PRD F-16; approved 19 Oct 2026).
    implementation(libs.androidx.glance.appwidget)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
}

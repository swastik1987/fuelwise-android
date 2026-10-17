plugins {
    alias(libs.plugins.fuelwise.android.library)
    alias(libs.plugins.fuelwise.android.compose)
    alias(libs.plugins.roborazzi)
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)

    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

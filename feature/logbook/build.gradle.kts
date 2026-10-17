plugins {
    alias(libs.plugins.fuelwise.android.feature)
}

dependencies {
    implementation(libs.androidx.paging.compose)
    testImplementation(libs.androidx.paging.testing)
}

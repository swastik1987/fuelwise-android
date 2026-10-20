plugins {
    alias(libs.plugins.fuelwise.android.feature)
}

dependencies {
    implementation(projects.core.notifications)
    // The price feed (PRD F-09.2): one static file over the OkHttp already used for Drive, fetched by WorkManager.
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.okhttp.mockwebserver)
}

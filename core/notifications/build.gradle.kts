plugins {
    alias(libs.plugins.fuelwise.android.library)
    alias(libs.plugins.fuelwise.android.compose)
    alias(libs.plugins.fuelwise.hilt)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}

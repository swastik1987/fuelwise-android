plugins {
    alias(libs.plugins.fuelwise.android.library)
    alias(libs.plugins.fuelwise.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
}

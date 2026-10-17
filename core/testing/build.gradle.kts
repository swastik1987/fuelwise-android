plugins {
    alias(libs.plugins.fuelwise.android.library)
}

dependencies {
    api(projects.core.data)
    api(projects.core.model)
    api(libs.kotlinx.coroutines.test)
    api(libs.junit4)
    implementation(libs.kotlinx.coroutines.core)
}

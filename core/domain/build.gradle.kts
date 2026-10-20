plugins {
    alias(libs.plugins.fuelwise.jvm.library)
}

dependencies {
    api(projects.core.model)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    // Feed and city-list parsing with the JsonElement API (no codegen plugin needed).
    implementation(libs.kotlinx.serialization.json)
}

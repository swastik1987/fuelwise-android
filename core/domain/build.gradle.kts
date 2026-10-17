plugins {
    alias(libs.plugins.fuelwise.jvm.library)
}

dependencies {
    api(projects.core.model)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}

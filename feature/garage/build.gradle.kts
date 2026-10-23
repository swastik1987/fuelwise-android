plugins {
    alias(libs.plugins.fuelwise.android.feature)
}

dependencies {
    // Car Bluetooth for automatic trips (PRD F-21.3): the chooser and the link live in core:location.
    implementation(projects.core.location)
}

plugins {
    alias(libs.plugins.fuelwise.android.feature)
}

dependencies {
    // Camera and on-device OCR (PRD F-15). The text model ships in the APK; nothing is downloaded or sent.
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
}

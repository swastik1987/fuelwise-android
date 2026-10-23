// Dev-only tool (PRD F-21.2, D16): builds the bundled OpenStreetMap station file. Never part of the app.
plugins {
    alias(libs.plugins.fuelwise.jvm.library)
    application
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("app.fuelwise.tools.stations.MainKt")
}

tasks.named<JavaExec>("run") {
    // Run from the repository root, so paths in the arguments read as written.
    workingDir = rootDir
}

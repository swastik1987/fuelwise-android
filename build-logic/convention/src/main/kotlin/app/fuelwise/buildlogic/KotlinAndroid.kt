package app.fuelwise.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Shared Android settings for application and library modules. */
internal fun Project.configureKotlinAndroid(android: CommonExtension) {
    android.compileSdk = Sdk.COMPILE
    android.defaultConfig.minSdk = Sdk.MIN
    android.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    android.testOptions.unitTests.isIncludeAndroidResources = true
    android.testOptions.unitTests.all {
        it.useJUnitPlatform()
        // Modules without tests yet (new feature modules) must not fail the build.
        it.failOnNoDiscoveredTests.set(false)
    }
    android.lint.apply {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // Version-freshness checks depend on the network and the day they run; they are not code problems.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
    configureKotlinCompile()
    addUnitTestDependencies()
}

/** Settings for pure Kotlin/JVM modules (core:model, core:domain). */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    configureKotlinCompile()
    addUnitTestDependencies()
}

private fun Project.configureKotlinCompile() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }
}

private fun Project.addUnitTestDependencies() {
    dependencies {
        add("testImplementation", platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testImplementation", libs.lib("truth"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
    }
}

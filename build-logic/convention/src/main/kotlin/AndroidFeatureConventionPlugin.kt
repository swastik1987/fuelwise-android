import app.fuelwise.buildlogic.lib
import app.fuelwise.buildlogic.libs
import app.fuelwise.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * A feature module: Android library + Compose + Hilt + type-safe navigation.
 * Feature modules depend only on core modules, never on each other (CLAUDE.md).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("fuelwise.android.library")
        pluginManager.apply("fuelwise.android.compose")
        pluginManager.apply("fuelwise.hilt")
        pluginManager.apply(libs.pluginId("kotlin-serialization"))
        pluginManager.apply(libs.pluginId("roborazzi"))
        dependencies {
            // ViewModel tests, Compose UI tests and Roborazzi screenshots run on the JVM with Robolectric.
            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("roborazzi"))
            add("testImplementation", libs.lib("roborazzi-compose"))
            add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            add("testImplementation", libs.lib("androidx-test-ext-junit"))
            add("testImplementation", libs.lib("androidx-test-core"))
            add("testRuntimeOnly", libs.lib("junit-vintage-engine"))
            add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
        }
    }
}

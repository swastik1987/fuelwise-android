import app.fuelwise.buildlogic.configureKotlinJvm
import app.fuelwise.buildlogic.configureQuality
import app.fuelwise.buildlogic.libs
import app.fuelwise.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Pure Kotlin module with no Android dependencies (core:model, core:domain). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        configureKotlinJvm()
        configureQuality()
    }
}

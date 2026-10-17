import app.fuelwise.buildlogic.Sdk
import app.fuelwise.buildlogic.configureKotlinAndroid
import app.fuelwise.buildlogic.configureQuality
import app.fuelwise.buildlogic.libs
import app.fuelwise.buildlogic.pluginId
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = Sdk.TARGET
        }
        configureQuality()
    }
}

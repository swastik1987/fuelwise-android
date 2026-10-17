import app.fuelwise.buildlogic.Sdk
import app.fuelwise.buildlogic.configureKotlinAndroid
import app.fuelwise.buildlogic.configureQuality
import app.fuelwise.buildlogic.libs
import app.fuelwise.buildlogic.pluginId
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            testOptions.targetSdk = Sdk.TARGET
            lint.targetSdk = Sdk.TARGET
            // Module namespace follows its Gradle path: :core:designsystem -> app.fuelwise.core.designsystem
            namespace = "app.fuelwise" + path.replace(':', '.').replace('-', '_')
        }
        configureQuality()
    }
}

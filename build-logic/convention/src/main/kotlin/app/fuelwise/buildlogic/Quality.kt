package app.fuelwise.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** ktlint + detekt on every module. Rules live in `.editorconfig` and `config/detekt/detekt.yml`. */
internal fun Project.configureQuality() {
    pluginManager.apply(libs.pluginId("ktlint"))
    pluginManager.apply(libs.pluginId("detekt"))

    extensions.configure<KtlintExtension> {
        version.set(libs.version("ktlint"))
        android.set(true)
        filter {
            exclude { it.file.path.contains("${java.io.File.separator}build${java.io.File.separator}") }
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
    }

    // detekt otherwise targets the JDK running Gradle, which can be newer than it understands.
    tasks.withType<Detekt>().configureEach { jvmTarget = "17" }

    // detekt 1.23 bundles its own Kotlin compiler; keep Gradle from upgrading it to the project's Kotlin.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(DETEKT_KOTLIN_VERSION)
        }
    }
}

private const val DETEKT_KOTLIN_VERSION = "2.0.21"

import androidx.room.gradle.RoomExtension
import app.fuelwise.buildlogic.lib
import app.fuelwise.buildlogic.libs
import app.fuelwise.buildlogic.pluginId
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room with exported schemas. Every schema version is committed under `schemas/`; migration tests read them
 * (CLAUDE.md rule 9: explicit migrations only, never destructive fallback).
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("ksp"))
        pluginManager.apply(libs.pluginId("room"))
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        // Migration tests also run as Robolectric unit tests (so CI covers them); they read the schemas as assets.
        extensions.configure<LibraryExtension> {
            sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.lib("androidx-room-runtime"))
            add("ksp", libs.lib("androidx-room-compiler"))
            add("testImplementation", libs.lib("androidx-room-testing"))
        }
    }
}

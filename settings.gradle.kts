pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Provisions the JDK named in gradle/gradle-daemon-jvm.properties, so every machine builds on the same JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fuelwise"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:domain")
include(":core:designsystem")
include(":core:ui")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:testing")
include(":core:notifications")
include(":core:location")
include(":sync:drive")
include(":feature:home")
include(":feature:logbook")
include(":feature:insights")
include(":feature:garage")
include(":feature:onboarding")
include(":feature:refuel")
include(":feature:expense")
include(":feature:reminders")
include(":feature:documents")
include(":feature:fastag")
include(":feature:backup")
include(":feature:dataimport")
include(":feature:trips")
include(":feature:settings")

plugins {
    alias(libs.plugins.fuelwise.android.application)
    alias(libs.plugins.fuelwise.android.compose)
    alias(libs.plugins.fuelwise.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets)
}

android {
    // `in` is a Kotlin keyword, so code lives under app.fuelwise while the store ID stays in.fuelwise.app.
    namespace = "app.fuelwise"

    defaultConfig {
        applicationId = "in.fuelwise.app"
        versionCode = 1
        versionName = "0.1.0"
        // The Secrets plugin sets the real key per variant; unit-test manifests merge without it, so give a default.
        manifestPlaceholders["MAPS_API_KEY"] = "not-configured"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        generateLocaleConfig = true
    }
    // Import reads labels in every shipped language (F-13.2) and the app switches language in-app (F-18), so all
    // languages stay in the base APK rather than Play language splits. EN + HI strings are small.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

// Keys come from local.properties (git-ignored); local.defaults.properties keeps CI building with empty values.
secrets {
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.notifications)
    implementation(projects.sync.drive)
    implementation(projects.feature.home)
    implementation(projects.feature.logbook)
    implementation(projects.feature.insights)
    implementation(projects.feature.garage)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.refuel)
    implementation(projects.feature.expense)
    implementation(projects.feature.reminders)
    implementation(projects.feature.documents)
    implementation(projects.feature.fastag)
    implementation(projects.feature.backup)
    implementation(projects.feature.dataimport)
    implementation(projects.feature.trips)
    implementation(projects.widget)
    implementation(projects.feature.scan)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}

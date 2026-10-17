plugins {
    alias(libs.plugins.fuelwise.android.application)
    alias(libs.plugins.fuelwise.android.compose)
    alias(libs.plugins.fuelwise.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // `in` is a Kotlin keyword, so code lives under app.fuelwise while the store ID stays in.fuelwise.app.
    namespace = "app.fuelwise"

    defaultConfig {
        applicationId = "in.fuelwise.app"
        versionCode = 1
        versionName = "0.1.0"
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
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.notifications)
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

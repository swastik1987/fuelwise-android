plugins {
    alias(libs.plugins.fuelwise.android.library)
    alias(libs.plugins.fuelwise.hilt)
    alias(libs.plugins.protobuf)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") { option("lite") }
                register("kotlin") { option("lite") }
            }
        }
    }
}

dependencies {
    api(libs.androidx.datastore)
    api(libs.protobuf.kotlin.lite)
    implementation(projects.core.domain)
    implementation(libs.kotlinx.coroutines.core)
}

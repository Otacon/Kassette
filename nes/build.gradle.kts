import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.metro)
}

val jvmToolchainVersion = providers.gradleProperty("jvmToolchainVersion").map(String::toInt).get()

kotlin {
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    jvmToolchain(jvmToolchainVersion)
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kotlinxSerializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }
    }
}

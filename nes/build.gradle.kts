import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotest)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxBenchmark)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
    kotlin("plugin.allopen") version libs.versions.kotlin
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
            implementation(libs.kotest)
            implementation(libs.kotestAssertions)
            implementation(libs.kotestFrameworkEngine)
        }
        jvmTest.dependencies {
            implementation(libs.kotestRunnerJunit)
            implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.18")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

benchmark {
    targets { register("jvmTest") }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}
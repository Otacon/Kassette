import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.buildKonfig)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.metro)
}

fun semVerToInt(version: String): String {
    val coreVersion = version
        .substringBefore('-')
        .substringBefore('+')

    val (major, minor, patch) = coreVersion
        .split(".")
        .map(String::toInt)

    return "%02d%02d%02d"
        .format(major, minor, patch)
        .toInt()
        .toString()
}

val propertyVersion = providers.gradleProperty("appVersion")

val isRelease = propertyVersion.orNull != null

val appVersion = propertyVersion
    .orElse("0.1.2-indev")
    .map { it.removePrefix("v") }
    .get()

project.version = appVersion

val jvmToolchainVersion = providers.gradleProperty("jvmToolchainVersion").map(String::toInt).get()

val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()

val isArm64 = osArch == "aarch64" || osArch == "arm64"

val desktopPlatform = when {
    osName.contains("win") && isArm64 -> "windows-arm64"
    osName.contains("win") -> "windows-x64"

    osName.contains("mac") && isArm64 -> "macos-arm64"
    osName.contains("mac") -> "macos-x64"

    osName.contains("linux") && isArm64 -> "linux-arm64"
    osName.contains("linux") -> "linux-x64"

    else -> error("Unsupported desktop platform: os.name=$osName, os.arch=$osArch")
}

val lwjglNatives = when (desktopPlatform) {
    "windows-x64" -> "natives-windows"
    "windows-arm64" -> "natives-windows-arm64"
    "macos-x64" -> "natives-macos"
    "macos-arm64" -> "natives-macos-arm64"
    "linux-x64" -> "natives-linux"
    "linux-arm64" -> "natives-linux-arm64"
    else -> error("Unsupported desktop platform: $desktopPlatform")
}

kotlin {
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "kassette.js"
            }
        }
        binaries.executable()
    }
    jvmToolchain(jvmToolchainVersion)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":nes"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.kermit)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kotlinxSerializationJson)
            implementation(libs.settings)
            implementation(libs.settingsSerialization)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinxBrowser)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.clikt)
            implementation(libs.kotlinxCoroutinesSwing)
            implementation(libs.jinput)
            runtimeOnly(dependencies.variantOf(libs.jinput) { classifier("natives-all") })

            implementation(libs.lwjgl)
            runtimeOnly(
                dependencies.variantOf(libs.lwjgl) { classifier(lwjglNatives) }
            )

            implementation(libs.lwjglOpenal)
            runtimeOnly(
                dependencies.variantOf(libs.lwjglOpenal) { classifier(lwjglNatives) }
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.MainKt"

        // LWJGL Memory-Safe backend (JVM 25+)
        // jvmArgs += listOf(
        //     "-Dorg.lwjgl.system.memoryBackend=ffm",
        //     "--enable-native-access=ALL-UNNAMED"
        // )

        nativeDistributions {
            val projectVersion = project.version as String
            packageName = "Kassette"
            modules("java.instrument", "java.management", "jdk.unsupported")

            macOS {
                iconFile.set(project.file("icons/kassette.icns"))
                val macVersion = semVerToInt(appVersion)
                packageVersion = macVersion
                packageBuildVersion = macVersion
            }

            windows {
                packageVersion = projectVersion
                iconFile.set(project.file("icons/kassette.ico"))
            }

            linux {
                packageVersion = projectVersion
                iconFile.set(project.file("icons/kassette.png"))
            }

        }

        if (osName.contains("mac")) {
            jvmArgs += "-Xdock:name=Kassette"
        }
    }
}

tasks.register<Zip>("zipDesktopDistribution") {
    group = "distribution"
    description = "Creates a ZIP containing the desktop application image."
    dependsOn("createDistributable")

    from(layout.buildDirectory.dir("compose/binaries/main/app"))

    archiveBaseName.set("kassette")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set(desktopPlatform)

    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    includeEmptyDirs = false

    filesMatching("**/*.app/Contents/MacOS/*") {
        permissions {
            unix("rwxr-xr-x")
        }
    }

    filesMatching("**/bin/*") {
        permissions {
            unix("rwxr-xr-x")
        }
    }
}

buildkonfig {
    packageName = "com.cyanotic.kassette"
    exposeObjectWithName = "BuildKonfig"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "version", appVersion)
        buildConfigField(FieldSpec.Type.INT, "loggingLevel", "${Severity.Info.ordinal}")
    }
    defaultConfigs("debug") {
        buildConfigField(FieldSpec.Type.STRING, "loggingLevel", "debug")
        buildConfigField(FieldSpec.Type.INT, "loggingLevel", "${Severity.Debug.ordinal}")
    }
}

enum class Severity {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert,
}

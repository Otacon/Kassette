plugins {
    alias(libs.plugins.kotlinJvm)
}

val jvmToolchainVersion = providers.gradleProperty("jvmToolchainVersion").map(String::toInt).get()

kotlin {
    jvmToolchain(jvmToolchainVersion)
}

dependencies {
    testImplementation(project(":nes"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${libs.versions.kotlin.get()}")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut")
        showStandardStreams = true
    }
}

sourceSets.test {
    resources.srcDir(project(":nes").layout.projectDirectory.dir("src/jvmTest/resources"))
}

package app

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.cyanotic.kassette.BuildKonfig
import com.github.ajalt.clikt.core.main
import dev.zacsweers.metro.createGraphFactory
import di.JvmFrontendComponent
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.Configuration

fun main(args: Array<String>) {
    // The macOS LWJGL bundle provides the async GLFW variant; Windows and Linux
    // provide only the standard GLFW native library.
    if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        Configuration.GLFW_LIBRARY_NAME.set("glfw_async")
    }
    GLFWErrorCallback.createPrint(System.out).set()
    val cliArgs = CliArgsParser()
    cliArgs.main(args)
    val minSeverity = if (cliArgs.debug) Severity.Debug else Severity.entries[BuildKonfig.loggingLevel]
    Logger.setMinSeverity(minSeverity)
    createGraphFactory<JvmFrontendComponent.Factory>()
        .create(cliArgs.asConfig())
        .emulatorApplication
        .run()
}

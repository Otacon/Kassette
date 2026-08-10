package app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import dev.zacsweers.metro.Inject
import frontend.Config
import nes.cartridge.RomData
import java.nio.file.Path
import kotlin.io.path.readBytes

@Inject
class CliArgsParser : CliktCommand("kassette") {
    val debug: Boolean by option(names = arrayOf("-d", "--debug"), help = "Enable debug logging")
        .flag()

    val rom: Path? by argument(help = "Path to .nes ROM or .zip archive")
        .path(mustExist = true, canBeDir = false)
        .optional()

    override fun run() = Unit

    fun asConfig(): Config {
        val romData = rom?.let { RomData(it.fileName.toString(), it.readBytes()) }
        return Config(
            debug = debug,
            rom = romData,
        )
    }

}
